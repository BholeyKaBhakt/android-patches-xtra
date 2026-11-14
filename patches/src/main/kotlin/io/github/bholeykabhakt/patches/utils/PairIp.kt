package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.mutableClassDefByOrNull
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import java.util.Base64

// Static de-PairIP: gut the native VM, then bake back the holder tables it would have populated
// from a per-build harvest (resources under /<app>/ + extensions/<app>depairip.mpe).

private const val SIGNATURE_CHECK = "Lcom/pairip/SignatureCheck;"
private const val VM_RUNNER = "Lcom/pairip/VMRunner;"
private const val STARTUP_LAUNCHER = "Lcom/pairip/StartupLauncher;"

/** The license-check entry that force-finishes a re-signed install; pass to [pairIpVmPatch] when present. */
const val PAIRIP_LICENSE_CHECK = "Lcom/pairip/licensecheck/LicenseClient;"

/** Nameless dependency that de-PairIPs [compatibility]'s app from its bundled `/<app>/` harvest. */
fun pairIpVmPatch(compatibility: Compatibility, app: String, vararg extraNoOps: Pair<String, String>) = patch {
    compatibleWith(compatibility)
    extendWith("extensions/${app}depairip.mpe")
    execute {
        gutPairIpVm(*extraNoOps)
        restorePairIpHolders(app)
    }
}

/**
 * Neutralise the VM and its re-sign check:
 *   - `SignatureCheck.verifyIntegrity` → no-op (re-sign check)
 *   - `VMRunner.<clinit>` → no-op (never loads `libpairipcore`)
 *   - `VMRunner.invoke` → null (dispatch into the VM)
 *   - `StartupLauncher.launch` → no-op (the startup program that fills the holder tables)
 * plus any app-specific [extraNoOps] (`class to method`).
 */
fun gutPairIpVm(vararg extraNoOps: Pair<String, String>) {
    mutableClassDefBy(SIGNATURE_CHECK).methods.first { it.name == "verifyIntegrity" }.returnEarly()

    val vmRunner = mutableClassDefBy(VM_RUNNER)
    vmRunner.methods.first { it.name == "<clinit>" }.returnEarly()
    vmRunner.methods.first { it.name == "invoke" }.returnEarly()

    mutableClassDefBy(STARTUP_LAUNCHER).methods.first { it.name == "launch" }.returnEarly()

    for ((classDescriptor, methodName) in extraNoOps) {
        mutableClassDefBy(classDescriptor).methods.first { it.name == methodName }.returnEarly()
    }
}

/**
 * Bake back the tables the gutted VM would have filled:
 *   - `depairip_strings.tsv`: `@<holder>` then `field<TAB>value` → a `const-string`/`sput-object` `<clinit>`
 *   - `depairip_methods.tsv`: `<holder><TAB><registers><TAB>base64(<clinit>)` → injected verbatim
 *     (reflect `Method`s into the de-virtualized `$c` classes from the `.mpe`)
 * Fails if a holder is missing or already has a `<clinit>` (the harvest is for another build).
 */
fun restorePairIpHolders(resourceDir: String) {
    fun resource(name: String) =
        object {}.javaClass.getResourceAsStream("/$resourceDir/$name")?.bufferedReader()?.readText()
            ?: error("de-PairIP resource missing: /$resourceDir/$name")

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

    // String-only holders: generate const-string/sput-object from the grouped table.
    var type: String? = null
    val body = StringBuilder()
    fun flushStrings() {
        val t = type ?: return
        body.append("return-void")
        bakeClinit(t, 1, body.toString())
        body.setLength(0)
        baked++
    }
    resource("depairip_strings.tsv").lineSequence()
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

    // Reflect-Method holders: inject the harvested <clinit> verbatim.
    resource("depairip_methods.tsv").lineSequence()
        .filter { it.isNotBlank() }
        .forEach { line ->
            val (t, registerCount, b64) = line.split('\t', limit = 3)
            bakeClinit(t, registerCount.toInt(), String(Base64.getDecoder().decode(b64)))
            baked++
        }

    if (baked == 0) error("de-PairIP: no holder data baked")
}
