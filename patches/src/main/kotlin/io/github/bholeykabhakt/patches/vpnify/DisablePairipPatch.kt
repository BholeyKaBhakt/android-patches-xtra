package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.returnEarly
import java.util.Base64

/**
 * Statically **de-PairIPs** stock vpnify so a re-signed build runs — a complete static
 * replacement for PairIP's dynamic native VM. Three steps:
 *
 *  1. **Gut the VM** — `SignatureCheck.verifyIntegrity` / `StartupLauncher.launch` → no-op,
 *     `VMRunner.<clinit>` (skip `loadLibrary`) + `VMRunner.invoke` → null.
 *  2. **Restore the string + reflect tables** PairIP would have populated, from two bundled
 *     resources: `vpnify/depairip_strings.tsv` (string-only holders → synthesised `<clinit>` of
 *     `const-string`/`sput-object`) and `vpnify/depairip_methods.tsv` (holders with reflect
 *     `Method` statics → base64 `<clinit>` body injected verbatim).
 *  3. **Restore the de-virtualized helper classes** the reflect table dispatches to, merged from
 *     the precompiled dex `extensions/vpnifydepairip.mpe` via [extendWith].
 *
 * The `.tsv`/`.mpe` resources are per-build (obfuscated names + harvested data); regenerate them
 * on a version bump. The gut logic itself is PairIP-generic.
 */
@Suppress("unused")
val disablePairipPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_VPNIFY)

    // The restored helper classes (new types) the reflect table dispatches to.
    extendWith("extensions/vpnifydepairip.mpe")

    execute {
        // 1) Neutralise the native VM + integrity check.
        mutableClassDefBy("Lcom/pairip/SignatureCheck;")
            .methods.first { it.name == "verifyIntegrity" }
            .returnEarly()

        val vmRunner = mutableClassDefBy("Lcom/pairip/VMRunner;")
        vmRunner.methods.first { it.name == "<clinit>" }.returnEarly()
        vmRunner.methods.first { it.name == "invoke" }.returnEarly()

        mutableClassDefBy("Lcom/pairip/StartupLauncher;")
            .methods.first { it.name == "launch" }
            .returnEarly()

        fun resource(path: String) =
            object {}.javaClass.getResourceAsStream(path)?.bufferedReader()?.readText()
                ?: error("vpnify de-PairIP resource missing: $path")

        // Synthesise the missing static initialiser on a holder and inject `body`.
        fun bakeClinit(type: String, registerCount: Int, body: String) {
            val holder = mutableClassDefByOrNull(type)
                ?: error("de-PairIP: holder $type not found (version mismatch?)")
            if (holder.methods.any { it.name == "<clinit>" }) {
                error("de-PairIP: $type unexpectedly already has a <clinit>")
            }
            val clinit = ImmutableMethod(
                type, "<clinit>", emptyList(), "V",
                AccessFlags.STATIC.value or AccessFlags.CONSTRUCTOR.value,
                null, null,
                ImmutableMethodImplementation(registerCount, emptyList(), null, null),
            ).toMutable()
            holder.methods.add(clinit)
            clinit.addInstructions(0, body)
        }

        var baked = 0

        // 2a) String-only holders: generate const-string/sput from the grouped table.
        var type: String? = null
        val body = StringBuilder()
        fun flushStrings() {
            val t = type ?: return
            body.append("return-void")
            bakeClinit(t, 1, body.toString())
            body.setLength(0)
            baked++
        }
        resource("/vpnify/depairip_strings.tsv").lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                if (line.startsWith("@")) {
                    flushStrings()
                    type = line.substring(1)
                } else {
                    val tab = line.indexOf('\t')
                    val field = line.substring(0, tab)
                    val value = line.substring(tab + 1)
                    body.append("const-string v0, \"").append(value).append("\"\n")
                        .append("sput-object v0, ").append(type).append("->")
                        .append(field).append(":Ljava/lang/String;\n")
                }
            }
        flushStrings()

        // 2b) Reflect-Method holders: inject the harvested <clinit> verbatim.
        resource("/vpnify/depairip_methods.tsv").lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val (t, registerCount, b64) = line.split('\t', limit = 3)
                bakeClinit(t, registerCount.toInt(), String(Base64.getDecoder().decode(b64)))
                baked++
            }

        if (baked == 0) error("de-PairIP: no holder data baked")
    }
}
