package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourceFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Byte-level patching of native libraries; each site asserts its expected words first. */

/** ARM64 instruction encodings (little-endian words) shared by native patches. */
object Arm64 {
    const val RET = 0xD65F03C0.toInt()
    const val NOP = 0xD503201F.toInt()
    const val MOV_W0_0 = 0x52800000
    const val MOV_W0_1 = 0x52800020

    private const val B_OPCODE = 0x14000000

    /** `B target` placed at [pc] (imm26, ±128 MB). */
    fun b(pc: Int, target: Int): Int {
        val byteOffset = target - pc
        if (byteOffset % 4 != 0) {
            throw PatchException("B target not 4-byte aligned (pc=${pc.hex()} target=${target.hex()})")
        }
        val wordOffset = byteOffset shr 2
        if (wordOffset !in -(1 shl 25) until (1 shl 25)) {
            throw PatchException("B target out of ±128MB range (pc=${pc.hex()} target=${target.hex()})")
        }
        return B_OPCODE or (wordOffset and 0x03FFFFFF)
    }
}

/** Replace the words at [offset] with [replacement] if [expected] is there (lengths may differ). */
class BinaryPatch(
    val label: String,
    val offset: Int,
    val expected: IntArray,
    val replacement: IntArray,
) {
    init {
        require(offset >= 0 && offset % 4 == 0) { "$label: offset ${offset.hex()} is not word-aligned" }
        require(expected.isNotEmpty() && replacement.isNotEmpty()) { "$label: empty expected/replacement" }
    }
}

/** Overwrite a function head with `RET`; only safe before any callee-saved push. */
fun retHead(label: String, offset: Int, vararg prologue: Int) =
    BinaryPatch(label, offset, prologue, intArrayOf(Arm64.RET))

/** Idempotent; validates every site before writing any. Returns the number of sites written. */
fun ByteArray.applyBinaryPatches(patches: List<BinaryPatch>, staleHint: String): Int {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    fun words(offset: Int, count: Int) = IntArray(count) { buffer.getInt(offset + it * 4) }

    val pending = patches.filter { p ->
        val span = maxOf(p.expected.size, p.replacement.size) * 4
        if (p.offset + span > size) {
            throw PatchException("${p.label} @ ${p.offset.hex()} is past the end of the library ($size bytes); $staleHint")
        }
        if (words(p.offset, p.replacement.size).contentEquals(p.replacement)) return@filter false
        val found = words(p.offset, p.expected.size)
        if (!found.contentEquals(p.expected)) {
            throw PatchException(
                "${p.label} @ ${p.offset.hex()}: expected ${p.expected.hexWords()}, " +
                        "found ${found.hexWords()}; $staleHint",
            )
        }
        true
    }
    pending.forEach { p -> p.replacement.forEachIndexed { i, w -> buffer.putInt(p.offset + i * 4, w) } }
    return pending.size
}

/** Applies [patches] to the native library at [path] (e.g. `lib/arm64-v8a/libil2cpp.so`). */
fun patchNativeLibrary(path: String, appLabel: String, version: String, patches: List<BinaryPatch>) {
    val so = resourceFile(path)
    if (!so.exists()) throw PatchException("$path not found in $appLabel APK")
    val bytes = so.readBytes()
    if (bytes.applyBinaryPatches(patches, "the $appLabel v$version offset table is stale") > 0) {
        so.writeBytes(bytes)
    }
}

/** The entry for [version], or a [PatchException] listing the supported versions and [howToAdd]. */
fun <T> Map<String, T>.forVersion(appLabel: String, version: String, howToAdd: String): T =
    this[version] ?: throw PatchException(
        "$appLabel version $version is not supported. Supported: ${keys.joinToString()}. $howToAdd",
    )

private fun Int.hex() = "0x" + toUInt().toString(16).uppercase()

private fun IntArray.hexWords() = joinToString(" ", "[", "]") { it.hex() }
