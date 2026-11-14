package io.github.bholeykabhakt.patches.all.branding

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ImageSize
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.colorOption
import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.imageOption
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.w3c.dom.Element
import java.io.File

private const val NS = "morphe_brand"
// Insets as a share of the canvas (InsetDrawable fractions, API 26+): the adaptive icon is 108 dp
// with a 66 dp safe circle; status-bar glyphs keep ~2 dp of a 24 dp box clear.
private const val INSET_LOGO = 28
private const val INSET_FULL = 17
private const val INSET_NOTIFICATION = 8
private const val BUILDER = "Landroid/app/Notification\$Builder;"
private const val SHIM = "Lio/github/bholeykabhakt/extension/branding/NotificationIcon;"
private const val EXTENSION_PREFIX = "Lio/github/bholeykabhakt/extension/"
private const val FCM_ICON = "com.google.firebase.messaging.default_notification_icon"
private val SPLASH_ICON = Regex(
    """(<item name="(?:android:)?windowSplashScreenAnimatedIcon">)[^<]*(</item>)""",
)

@Suppress("unused")
val customBrandingPatch = patch(
    name = "Custom name and icon",
    default = false,
) {
    extendWith("extensions/branding.mpe")

    val appName by stringOption(
        key = "appName",
        default = null,
        title = "App name",
        description = "Name under the icon and in Settings. Empty keeps the original (or the preset's name).",
    )
    val preset by stringOption(
        key = "preset",
        default = null,
        values = mapOf("None" to "") + BRANDING_PRESETS.map { (key, p) -> p.label to key },
        title = "Preset",
        description = "A built-in look (icon and name). Ignored when an icon image is set.",
    )
    val iconImage by imageOption(
        key = "iconImage",
        default = null,
        title = "Icon image",
        description = "Square PNG, JPEG or WebP. A PNG with transparency is also used for the themed and notification icons.",
        allowedExtensions = listOf("png", "jpg", "jpeg", "webp"),
        recommendedSize = ImageSize(512, 512),
    )
    val background by colorOption(
        key = "background",
        default = "#FFFFFF",
        title = "Icon background",
        description = "Shown behind the icon image.",
    )
    val fullBleed by booleanOption(
        key = "fullBleed",
        default = false,
        title = "Fill the icon",
        description = "Stretch the image to the icon edge (photos). Off leaves a margin (logos).",
    )
    val silhouette by imageOption(
        key = "silhouette",
        default = null,
        title = "Silhouette image",
        description = "Transparent PNG for the themed and notification icons, when the icon image is a photo.",
        allowedExtensions = listOf("png"),
        recommendedSize = ImageSize(256, 256),
    )
    val renameInApp by booleanOption(
        key = "renameInApp",
        default = false,
        title = "Rename inside the app",
        description = "Also replace the app's own name string, which it may show on its screens.",
    )

    execute {
        val image = iconImage?.takeIf { it.isNotBlank() }?.let(::File)
        val glyph = silhouette?.takeIf { it.isNotBlank() }?.let(::File)
        val look = if (image == null) preset?.takeIf { it.isNotBlank() }?.let {
            BRANDING_PRESETS[it] ?: throw PatchException("Unknown preset $it")
        } else null
        val name = appName?.takeIf { it.isNotBlank() } ?: look?.label

        image?.let { if (!it.isFile) throw PatchException("Icon image not found: $it") }
        glyph?.let { if (!it.isFile) throw PatchException("Silhouette image not found: $it") }

        val icon = image != null || look != null
        val notification = when {
            image != null -> {
                val bytes = image.readBytes()
                // Themed and status-bar icons are drawn from alpha only, so they need transparency.
                val mask = glyph?.readBytes() ?: bytes.takeIf(::hasAlpha)
                writeIcon(
                    Layer(bytes, image.extension, if (fullBleed == true) INSET_FULL else INSET_LOGO),
                    mask?.let { Layer(it, "png", INSET_LOGO) },
                    mask?.let { Layer(it, "png", INSET_NOTIFICATION) },
                )
            }
            look != null -> {
                val key = preset!!
                val custom = glyph?.readBytes()
                writeIcon(
                    Layer(presetImage(key), "webp", 0), // pre-placed
                    custom?.let { Layer(it, "png", INSET_LOGO) } ?: Layer(presetMono(key), "png", 0),
                    Layer(custom ?: presetGlyph(key), "png", INSET_NOTIFICATION),
                )
            }
            else -> false
        }

        // The resource encoder wants each value type in its own file (strings.xml, colors.xml).
        if (name != null) addValue("strings.xml", "<string name=\"${NS}_name\">${escape(name)}</string>")
        if (icon) addValue("colors.xml", "<color name=\"${NS}_background\">${look?.background ?: background ?: "#FFFFFF"}</color>")

        val appLabel = editManifest(name, icon, notification)
        if (name != null && renameInApp == true && appLabel?.startsWith("@string/") == true) {
            renameString(appLabel.removePrefix("@string/"), name)
        }
        if (icon) pointSplashAtIcon()
        if (notification) rerouteSmallIcons()
    }
}

// ---- resources ----------------------------------------------------------------------------------

private fun write(path: String, text: String) {
    resourceFile(path, true).apply { parentFile?.mkdirs() }.writeText(text)
}

private fun addValue(file: String, element: String) {
    val f = resourceFile("res/values/$file", true)
    if (!f.isFile) {
        f.parentFile?.mkdirs()
        f.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n")
    }
    val text = f.readText()
    val end = text.lastIndexOf("</resources>")
    if (end < 0) throw PatchException("res/values/$file is not a resources file")
    f.writeText(text.substring(0, end) + "    $element\n" + text.substring(end))
}

private fun adaptive(monochrome: Boolean) = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/${NS}_background"/>
    <foreground android:drawable="@drawable/${NS}_foreground"/>
    ${if (monochrome) "<monochrome android:drawable=\"@drawable/${NS}_monochrome\"/>" else ""}
</adaptive-icon>
"""

/** Pre-Oreo launchers can't read adaptive icons: same layers, flattened. */
private val LEGACY = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape android:shape="oval"><solid android:color="@color/${NS}_background"/></shape></item>
    <item android:drawable="@drawable/${NS}_foreground"/>
</layer-list>
"""

private fun writeLauncher(monochrome: Boolean) {
    write("res/mipmap-anydpi-v26/${NS}_launcher.xml", adaptive(monochrome))
    write("res/mipmap/${NS}_launcher.xml", LEGACY)
}

/** An image for one icon slot and its inset, as a share of the canvas. */
private class Layer(val bytes: ByteArray, val extension: String, val insetPercent: Int)

/** Copies the images untouched (Android scales them). Returns whether a notification icon exists. */
private fun writeIcon(foreground: Layer, monochrome: Layer?, notification: Layer?): Boolean {
    insetDrawable("${NS}_foreground", copy("${NS}_image", foreground), foreground.insetPercent)
    monochrome?.let { insetDrawable("${NS}_monochrome", copy("${NS}_mono", it), it.insetPercent) }
    notification?.let { insetDrawable("${NS}_notification", copy("${NS}_glyph", it), it.insetPercent) }
    writeLauncher(monochrome = monochrome != null)
    return notification != null
}

private fun copy(name: String, layer: Layer): String {
    val ext = when (layer.extension.lowercase()) {
        "jpeg", "jpg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        else -> throw PatchException("Unsupported image type: .${layer.extension}")
    }
    writeBytes("res/drawable-nodpi/$name.$ext", layer.bytes)
    return name
}

private fun writeBytes(path: String, bytes: ByteArray) {
    resourceFile(path, true).apply { parentFile?.mkdirs() }.writeBytes(bytes)
}

/** Fractional inset on API 26+; a plain bitmap below, where InsetDrawable takes no fractions. */
private fun insetDrawable(name: String, bitmap: String, percent: Int) {
    val plain = """<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/$bitmap" android:gravity="fill" android:filter="true"/>"""
    write("res/drawable/$name.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$plain\n")
    write(
        "res/drawable-v26/$name.xml",
        """<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="$percent%">
    <bitmap android:src="@drawable/$bitmap" android:gravity="fill" android:filter="true"/>
</inset>
""",
    )
}

/** PNG colour type 4/6 or a tRNS chunk; WebP VP8X alpha flag or lossless. */
internal fun hasAlpha(b: ByteArray): Boolean {
    if (b.size > 26 && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte()) {
        val type = b[25].toInt()
        return type == 4 || type == 6 || String(b, Charsets.ISO_8859_1).contains("tRNS")
    }
    if (b.size > 21 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP") {
        return when (String(b, 12, 4, Charsets.ISO_8859_1)) {
            "VP8X" -> b[20].toInt() and 0x10 != 0
            "VP8L" -> true
            else -> false
        }
    }
    return false
}

private fun escape(s: String) = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    .let { if (it.startsWith("@") || it.startsWith("?")) "\\$it" else it }

// ---- manifest -----------------------------------------------------------------------------------

/** Points labels/icons at the new resources. Returns the application's original label. */
private fun editManifest(name: String?, icon: Boolean, notification: Boolean): String? {
    var original: String? = null
    document("AndroidManifest.xml").use { doc ->
        val app = doc.getElementsByTagName("application").item(0) as Element
        original = app.getAttribute("android:label").ifEmpty { null }
        if (name != null) app.setAttribute("android:label", "@string/${NS}_name")
        if (icon) {
            app.setAttribute("android:icon", "@mipmap/${NS}_launcher")
            app.setAttribute("android:roundIcon", "@mipmap/${NS}_launcher")
        }

        for (tag in listOf("activity", "activity-alias")) {
            val nodes = app.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                val launcher = isLauncher(e)
                val label = e.getAttribute("android:label")
                if (name != null && (launcher && label.isNotEmpty() || label == original)) {
                    e.setAttribute("android:label", "@string/${NS}_name")
                }
                if (icon && launcher) {
                    if (e.hasAttribute("android:icon")) e.setAttribute("android:icon", "@mipmap/${NS}_launcher")
                    if (e.hasAttribute("android:roundIcon")) e.setAttribute("android:roundIcon", "@mipmap/${NS}_launcher")
                }
            }
        }

        if (notification) {
            val meta = app.getElementsByTagName("meta-data")
            for (i in 0 until meta.length) {
                val e = meta.item(i) as Element
                if (e.getAttribute("android:name") == FCM_ICON) {
                    e.setAttribute("android:resource", "@drawable/${NS}_notification")
                }
            }
        }
    }
    return original
}

private fun isLauncher(activity: Element): Boolean {
    val categories = activity.getElementsByTagName("category")
    for (i in 0 until categories.length) {
        val c = (categories.item(i) as Element).getAttribute("android:name")
        if (c == "android.intent.category.LAUNCHER" || c == "android.intent.category.LEANBACK_LAUNCHER") return true
    }
    return false
}

// ---- values in every qualifier ------------------------------------------------------------------

private fun valuesFiles(name: String): List<File> =
    resourceFile("res", true).listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        .map { File(it, name) }.filter { it.isFile }

private fun renameString(key: String, value: String) {
    val pattern = Regex("""(<string name="${Regex.escape(key)}"[^>]*>)[\s\S]*?(</string>)""")
    for (f in valuesFiles("strings.xml")) {
        val text = f.readText()
        val out = pattern.replace(text) { "${it.groupValues[1]}${escape(value)}${it.groupValues[2]}" }
        if (out != text) f.writeText(out)
    }
}

/** Android 12+ splash: themes that name their own icon would still show the original. */
private fun pointSplashAtIcon() {
    for (file in valuesFiles("styles.xml") + valuesFiles("themes.xml")) {
        val text = file.readText()
        val out = SPLASH_ICON.replace(text) { "${it.groupValues[1]}@mipmap/${NS}_launcher${it.groupValues[2]}" }
        if (out != text) file.writeText(out)
    }
}

// ---- notifications ------------------------------------------------------------------------------

/** Every `Notification.Builder.setSmallIcon(...)` call goes through [SHIM] instead. */
private fun rerouteSmallIcons() {
    classDefForEach { classDef ->
        if (classDef.type.startsWith(EXTENSION_PREFIX)) return@classDefForEach
        val hits = classDef.methods.any { m ->
            m.implementation?.instructions?.any { insn ->
                insn.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((insn as ReferenceInstruction).reference as MethodReference).let {
                        it.definingClass == BUILDER && it.name == "setSmallIcon"
                    }
            } == true
        }
        if (!hits) return@classDefForEach

        mutableClassDefBy(classDef).methods.forEach { method ->
            val impl = method.implementation ?: return@forEach
            for (i in 0 until impl.instructions.size) {
                val insn = impl.getInstruction(i)
                if (insn.opcode != Opcode.INVOKE_VIRTUAL) continue
                val ref = (insn as ReferenceInstruction).reference as MethodReference
                if (ref.definingClass != BUILDER || ref.name != "setSmallIcon") continue
                val r = insn as FiveRegisterInstruction
                // Same registers, static form with the builder as first parameter; returns the builder.
                val target = ImmutableMethodReference(SHIM, "setSmallIcon", listOf(BUILDER) + ref.parameterTypes.map { it.toString() }, BUILDER)
                method.replaceInstruction(
                    i,
                    BuilderInstruction35c(Opcode.INVOKE_STATIC, r.registerCount, r.registerC, r.registerD, r.registerE, r.registerF, r.registerG, target),
                )
            }
        }
    }
}
