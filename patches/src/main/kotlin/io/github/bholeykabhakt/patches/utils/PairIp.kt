package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import java.util.Base64

// Shared "heavy" PairIP de-VM helpers. PairIP's native VM (libpairipcore.so) SIGSEGVs on a
// re-signed APK and, at launch, bulk-populates Java static holder tables the app's stubs read;
// gutting the VM leaves those null (→ crashes). The fix is the same for any PairIP-protected app —
// gut the VM, then bake the VM-populated tables back from a per-build harvest — so it lives here
// once. Each patch supplies its own bundled harvest resources + de-virtualized `.mpe` dex.

private const val SIGNATURE_CHECK = "Lcom/pairip/SignatureCheck;"
private const val VM_RUNNER = "Lcom/pairip/VMRunner;"
private const val STARTUP_LAUNCHER = "Lcom/pairip/StartupLauncher;"

/**
 * Neutralise PairIP's native VM + re-sign integrity check:
 *   - `SignatureCheck.verifyIntegrity` → no-op (the SHA re-sign check)
 *   - `VMRunner.<clinit>` → no-op (skips `loadLibrary("pairipcore")`, so the VM never loads)
 *   - `VMRunner.invoke` → null (the dispatch into the native VM)
 *   - `StartupLauncher.launch` → no-op (the startup VM program that populated the tables)
 *
 * [extraNoOps] are app-specific `classDescriptor to methodName` pairs to also force-return early
 * (e.g. a `licensecheck.LicenseClient.checkLicense` Play-license boot blocker, when present).
 */
fun BytecodePatchContext.gutPairIpVm(vararg extraNoOps: Pair<String, String>) {
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
 * Restore the holder tables the gutted VM would have populated, from the bundled per-build harvest
 * resources under `/<resourceDir>/` (the app's resource bundle name):
 *   - `depairip_strings.tsv` — `@<holder>` group headers then `field<TAB>value`; each holder gets a
 *     synthesised `<clinit>` of `const-string`/`sput-object` (the stock holders have none — the VM
 *     set them at runtime).
 *   - `depairip_methods.tsv` — `<holder><TAB><registerCount><TAB>base64(<clinit> body)`; the decoded
 *     reflect-`Method` `<clinit>` (`getDeclaredMethod` into the de-virtualized `$c` classes) is
 *     injected verbatim.
 *
 * Fail-loud on version drift: errors if a holder is missing or already has a `<clinit>` (either
 * means the harvest no longer matches the APK → regenerate). The de-virtualized `$c` body classes
 * are merged separately by each patch via `extendWith("extensions/<app>depairip.mpe")`.
 */
fun BytecodePatchContext.restorePairIpHolders(resourceDir: String) {
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
