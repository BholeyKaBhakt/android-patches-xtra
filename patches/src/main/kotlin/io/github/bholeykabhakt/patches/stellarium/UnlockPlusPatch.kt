package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.Elf64
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary

/**
 * Stellarium Mobile (Qt6 / C++): unlocks Plus in the arm64 engine library. Sites are found by their
 * exported symbols, so new releases need no offsets:
 *  - `StelAPI::getHasFeaturePLUS` / `hasValidLicense` return a `QVariant` through the x8 sret buffer;
 *    each entry becomes `MOV X0,X8 ; MOV W1,#1 ; B QVariant::QVariant(bool)`;
 *  - `StelAPI::getAppVariant`'s `LDRB ; CBZ` to the "free" arm is NOP-ed so it reports "plus",
 *    which drops the upsell menu item.
 */

private const val LIB_PATH = "lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so"

private const val MOV_X0_X8 = 0xAA0803E0.toInt()      // MOV X0, X8
private const val MOV_W1_1 = 0x52800021                // MOV W1, #1

private const val QVARIANT_BOOL = "_ZN8QVariantC1Eb"
private const val HAS_FEATURE_PLUS = "_ZNK7StelAPI17getHasFeaturePLUSEv"
private const val HAS_VALID_LICENSE = "_ZNK7StelAPI15hasValidLicenseEv"
private const val APP_VARIANT = "_ZNK7StelAPI13getAppVariantEv"

/** Entry → tail-call returning `QVariant(true)`, after proving the getter returns a QVariant via x8. */
private fun Elf64.returnQVariantTrue(name: String, ctor: Long): BinaryPatch {
    val fn = requireSymbol(name)
    val pcs = addresses(fn)
    if (pcs.none { Arm64.branchTarget(it, word(it)) == ctor }) {
        throw PatchException("Stellarium: $name no longer builds a QVariant(bool)")
    }
    // MOV Xd, X8 (ORR Xd, XZR, X8) — the sret buffer is consumed, so the return is a QVariant.
    if (pcs.none { word(it) and 0xFFFFFFE0.toInt() == MOV_X0_X8 }) {
        throw PatchException("Stellarium: $name does not return through x8")
    }
    val entry = fn.vaddr
    return BinaryPatch(
        "StelAPI::$name", fileOffset(entry),
        expected = intArrayOf(word(entry), word(entry + 4), word(entry + 8)),
        replacement = intArrayOf(MOV_X0_X8, MOV_W1_1, Arm64.b(pc = (entry + 8).toInt(), target = ctor.toInt())),
    )
}

/** `LDRB Wn, [..] ; CBZ Wn, <free>` → NOP the CBZ, after checking its target loads "free". */
private fun Elf64.forcePlusVariant(): BinaryPatch {
    val pcs = addresses(requireSymbol(APP_VARIANT))
    val cbz = pcs.zipWithNext().firstOrNull { (prev, pc) ->
        val ldrb = word(prev)
        val insn = word(pc)
        Arm64.isLdrbW(ldrb) && Arm64.isCbzW(insn) && Arm64.rt(ldrb) == Arm64.rt(insn) &&
            loadsString(Arm64.cbTarget(pc, insn)) == "free"
    }?.second
        // Already patched: the flag load is followed by our NOP.
        ?: pcs.zipWithNext().firstOrNull { (prev, pc) -> Arm64.isLdrbW(word(prev)) && word(pc) == Arm64.NOP }?.second
        ?: throw PatchException("Stellarium: getAppVariant() flag branch not found")
    return BinaryPatch("StelAPI::getAppVariant() force plus", fileOffset(cbz), intArrayOf(word(cbz)), intArrayOf(Arm64.NOP))
}

private fun Elf64.loadsString(pc: Long): String? {
    val adrp = word(pc)
    val add = word(pc + 4)
    if (!Arm64.isAdrp(adrp) || !Arm64.isAddImmX(add)) return null
    return runCatching { string(Arm64.adrpTarget(pc, adrp) + Arm64.addImm(add)) }.getOrNull()
}

internal fun stellariumPlusPatches(lib: ByteArray): List<BinaryPatch> {
    val elf = Elf64(lib)
    val ctor = elf.pltStub(QVARIANT_BOOL) ?: throw PatchException("Stellarium: QVariant(bool) import not found")
    return listOf(
        elf.returnQVariantTrue(HAS_FEATURE_PLUS, ctor),
        elf.returnQVariantTrue(HAS_VALID_LICENSE, ctor),
        elf.forcePlusVariant(),
    )
}

@Suppress("unused")
val unlockPlusPatch = patch(
    name = "Unlock Plus",
) {
    compatibleWith(COMPATIBILITY_STELLARIUM)

    dependsOn(disableAnalyticsPatch)

    execute {
        patchNativeLibrary(LIB_PATH, "Stellarium", "Stellarium library layout changed", ::stellariumPlusPatches)
    }
}
