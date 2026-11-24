package io.github.bholeykabhakt.patches.everycircuit

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_EVERYCIRCUIT
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.Elf64
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary

/**
 * EveryCircuit keeps its licence in the native `MMLicenseManager` (`libinterface.so`, symbols exported):
 * a vector of `MMFeature` records, each licensed while its expiry is non-zero, plus two parameters from
 * the cloud — the device count a circuit may have and still simulate, and the number of saved circuits.
 *
 * `enablePermanentFeatures()` runs at the end of every feature update (defaults at start-up and each
 * cloud sync) and, for the paid editions only, marks a fixed feature set permanent. It is rewritten to
 * mark every feature permanent with the same state and expiry, and to lift both limits. Offsets come
 * from the exported accessors, so no per-version table is needed.
 */

private const val LIB_PATH = "lib/arm64-v8a/libinterface.so"

private const val ENABLE_PERMANENT = "_ZN16MMLicenseManager23enablePermanentFeaturesEv"
private const val GET_FEATURES = "_ZN16MMLicenseManager11getFeaturesEv"
private const val GET_FEATURE_BY_ID = "_ZN16MMLicenseManager14getFeatureByIdEi"
private const val IS_SIMULATABLE = "_ZNK16MMLicenseManager13isSimulatableE10MMDocLabelib"
private const val IS_SAVABLE = "_ZNK16MMLicenseManager9isSavableEbi"

private fun Int.bits(shift: Int, width: Int) = (this ushr shift) and ((1 shl width) - 1)

private fun Elf64.words(name: String): List<Int> = words(requireSymbol(name))

/** `ADD X0, X0, #imm ; RET` → imm: where the feature vector sits in the manager. */
private fun Elf64.featureVectorOffset(): Int {
    val (add, ret) = words(GET_FEATURES).take(2).let { it[0] to it.getOrNull(1) }
    if (add and 0xFFC003FF.toInt() != 0x91000000.toInt() || ret != Arm64.RET) {
        throw PatchException("EveryCircuit: getFeatures() is no longer `add x0, x0, #imm`")
    }
    return add.bits(10, 12)
}

/** `MOV Wn, #stride` in `getFeatureById`: the size of one `MMFeature`. */
private fun Elf64.featureStride(): Int =
    words(GET_FEATURE_BY_ID).firstOrNull { it and 0xFFE00000.toInt() == 0x52800000 }?.bits(5, 16)
        ?: throw PatchException("EveryCircuit: getFeatureById() stride not found")

/** `LDR Wt, [X0, #off] ; CMN Wt, #1` — the limit field a predicate compares against -1 (unlimited). */
private fun Elf64.limitOffset(name: String): Int =
    words(name).zipWithNext().firstOrNull { (ldr, cmn) ->
        ldr and 0xFFC00000.toInt() == 0xB9400000.toInt() && ldr.bits(5, 5) == 0 &&
            cmn and 0xFFFFFC1F.toInt() == 0x3100041F && cmn.bits(5, 5) == ldr.bits(0, 5)
    }?.first?.bits(10, 12)?.times(4) ?: throw PatchException("EveryCircuit: limit field not found in $name")

private class PermanentFeature(val state: Int, val stateOffset: Int, val expiry: Long, val expiryOffset: Int)

/** The state and expiry the stock function writes, and the record fields they go to. */
private fun Elf64.permanentFeature(): PermanentFeature {
    val body = words(ENABLE_PERMANENT)
    val movzW = HashMap<Int, Int>()
    val wide = HashMap<Int, Long>()
    var state: Pair<Int, Int>? = null
    var expiry: Pair<Long, Int>? = null
    for (insn in body) {
        when {
            insn and 0xFFE00000.toInt() == 0x52800000 -> movzW[insn.bits(0, 5)] = insn.bits(5, 16)
            insn and 0xFF800000.toInt() == 0xD2800000.toInt() ->
                wide[insn.bits(0, 5)] = insn.bits(5, 16).toLong() shl (16 * insn.bits(21, 2))
            insn and 0xFF800000.toInt() == 0xF2800000.toInt() -> wide.computeIfPresent(insn.bits(0, 5)) { _, v ->
                val shift = 16 * insn.bits(21, 2)
                (v and (0xFFFFL shl shift).inv()) or (insn.bits(5, 16).toLong() shl shift)
            }
            insn and 0xFFC00000.toInt() == 0xB9000000.toInt() -> movzW[insn.bits(0, 5)]?.let { state = it to insn.bits(10, 12) * 4 }
            insn and 0xFFC00000.toInt() == 0xF9000000.toInt() -> wide[insn.bits(0, 5)]?.let { expiry = it to insn.bits(10, 12) * 8 }
        }
    }
    val (s, so) = state ?: throw PatchException("EveryCircuit: permanent state store not found")
    val (e, eo) = expiry ?: throw PatchException("EveryCircuit: permanent expiry store not found")
    if (e < 1_000_000_000_000L) throw PatchException("EveryCircuit: permanent expiry $e is not a timestamp")
    return PermanentFeature(s, so, e, eo)
}

/**
 * The offsets, stride and limits come from the library at patch time, so each encoder checks its
 * operand fits the field: an out-of-range value would otherwise silently encode another instruction.
 */
private object A64 {
    private fun check(ok: Boolean, what: () -> String) {
        if (!ok) throw PatchException("EveryCircuit: ${what()} doesn't fit the instruction encoding")
    }
    private fun imm16(imm: Int) = check(imm in 0..0xFFFF) { "immediate $imm" }
    private fun scaled(off: Int, scale: Int, range: IntRange) =
        check(off % scale == 0 && off in range) { "offset $off (scale $scale)" }

    fun ldpX(rt: Int, rt2: Int, rn: Int, off: Int): Int {
        scaled(off, 8, -512..504) // signed imm7 * 8
        return 0xA9400000.toInt() or ((off / 8 and 0x7F) shl 15) or (rt2 shl 10) or (rn shl 5) or rt
    }
    fun movzX(rd: Int, imm: Int, hw: Int) = imm16(imm).let { 0xD2800000.toInt() or (hw shl 21) or (imm shl 5) or rd }
    fun movkX(rd: Int, imm: Int, hw: Int) = imm16(imm).let { 0xF2800000.toInt() or (hw shl 21) or (imm shl 5) or rd }
    fun movzW(rd: Int, imm: Int) = imm16(imm).let { 0x52800000 or (imm shl 5) or rd }
    fun movnW(rd: Int, imm: Int) = imm16(imm).let { 0x12800000 or (imm shl 5) or rd }
    fun strW(rt: Int, rn: Int, off: Int) = scaled(off, 4, 0..4095 * 4).let { 0xB9000000.toInt() or ((off / 4) shl 10) or (rn shl 5) or rt }
    fun strX(rt: Int, rn: Int, off: Int) = scaled(off, 8, 0..4095 * 8).let { 0xF9000000.toInt() or ((off / 8) shl 10) or (rn shl 5) or rt }
    fun cmpX(rn: Int, rm: Int) = 0xEB00001F.toInt() or (rm shl 16) or (rn shl 5)
    fun bHs(words: Int) = check(words in -(1 shl 18) until (1 shl 18)) { "branch $words" }
        .let { 0x54000002 or ((words and 0x7FFFF) shl 5) }
    fun addX(rd: Int, rn: Int, imm: Int) = check(imm in 0..4095) { "add immediate $imm" }
        .let { 0x91000000.toInt() or (imm shl 10) or (rn shl 5) or rd }
    fun b(words: Int) = check(words in -(1 shl 25) until (1 shl 25)) { "branch $words" }
        .let { 0x14000000 or (words and 0x03FFFFFF) }
}

internal fun everyCircuitPatches(lib: ByteArray): List<BinaryPatch> {
    val elf = Elf64(lib)
    val fn = elf.requireSymbol(ENABLE_PERMANENT)
    val vector = elf.featureVectorOffset()
    val first = elf.word(fn.vaddr)
    if (first == A64.ldpX(8, 9, 0, vector)) return emptyList() // already rewritten

    val stride = elf.featureStride()
    val feature = elf.permanentFeature()
    val simLimit = elf.limitOffset(IS_SIMULATABLE)
    val saveLimit = elf.limitOffset(IS_SAVABLE)
    if (feature.stateOffset >= stride || feature.expiryOffset + 8 > stride) {
        throw PatchException("EveryCircuit: feature fields fall outside a $stride-byte record")
    }

    val e = feature.expiry
    val code = intArrayOf(
        A64.ldpX(8, 9, 0, vector),                     // x8 = features.begin, x9 = features.end
        A64.movzX(10, (e and 0xFFFF).toInt(), 0),      // x10 = permanent expiry
        A64.movkX(10, (e ushr 16 and 0xFFFF).toInt(), 1),
        A64.movkX(10, (e ushr 32 and 0xFFFF).toInt(), 2),
        A64.movkX(10, (e ushr 48 and 0xFFFF).toInt(), 3),
        A64.movzW(11, feature.state),                  // w11 = permanent state
        A64.movnW(12, 0),                              // w12 = -1 (unlimited)
        A64.strW(12, 0, simLimit),
        A64.strW(12, 0, saveLimit),
        A64.cmpX(8, 9),                                // loop:
        A64.bHs(5),                                    //   b.hs done
        A64.strW(11, 8, feature.stateOffset),
        A64.strX(10, 8, feature.expiryOffset),
        A64.addX(8, 8, stride),
        A64.b(-5),                                     //   b loop
        Arm64.RET,                                     // done:
    )
    if (fn.size < code.size * 4) throw PatchException("EveryCircuit: enablePermanentFeatures() is too small to rewrite")
    val expected = IntArray(code.size) { elf.word(fn.vaddr + it * 4L) }
    return listOf(BinaryPatch("MMLicenseManager::enablePermanentFeatures()", elf.fileOffset(fn.vaddr), expected, code))
}

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_EVERYCIRCUIT)

    execute {
        patchNativeLibrary(LIB_PATH, "EveryCircuit", "EveryCircuit library layout changed", ::everyCircuitPatches)
    }
}
