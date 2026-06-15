package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sky Tonight (Unity / IL2CPP). Three ARM64 function-head rewrites in `libil2cpp.so` unlock
 * premium:
 *
 *   1. `GooglePlay.InvalidLicense()` → `MOV W8,#1 ; STRB W8,[X0,#0x48] ; RET`
 *   2. `StoreInitializer.get_PremiumAccessEnabled()` → `MOV W0,#1 ; RET`
 *   3. `StoreInitializer.get_LockedItemsCount()` → `MOV W0,#0`
 *
 * Offsets are per-version; expected bytes are asserted at each site before writing.
 */

// ARM64 instruction encodings (little-endian)
private const val MOV_W0_0 = 0x52800000             // MOV W0, #0
private const val MOV_W0_1 = 0x52800020             // MOV W0, #1
private const val MOV_W0_3 = 0x52800060             // MOV W0, #3
private const val MOV_W8_1 = 0x52800028             // MOV W8, #1
private const val STRB_W8_X0_48 = 0x39012008        // STRB W8, [X0, #0x48]
private const val RET = 0xD65F03C0.toInt()          // RET
private const val STP_X30_X19 = 0xA9BF4FFE.toInt()  // STP X30, X19, [SP, #-0x10]!
private const val MOV_X19_X0 = 0xAA0003F3.toInt()   // MOV X19, X0
private const val LDR_X8_X0_20 = 0xF9401008.toInt() // LDR X8, [X0, #0x20]

private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

private data class BinaryPatch(
    val label: String,
    val offset: Int,
    val expected: IntArray,
    val replacement: IntArray,
)

private fun invalidLicense(offset: Int) = BinaryPatch(
    label = "GooglePlay.InvalidLicense()",
    offset = offset,
    expected = intArrayOf(STP_X30_X19, LDR_X8_X0_20, MOV_X19_X0),
    replacement = intArrayOf(MOV_W8_1, STRB_W8_X0_48, RET),
)

private fun premiumAccess(offset: Int) = BinaryPatch(
    label = "StoreInitializer.get_PremiumAccessEnabled()",
    offset = offset,
    expected = intArrayOf(STP_X30_X19, MOV_X19_X0),
    replacement = intArrayOf(MOV_W0_1, RET),
)

private fun lockedItems(offset: Int) = BinaryPatch(
    label = "StoreInitializer.get_LockedItemsCount()",
    offset = offset,
    expected = intArrayOf(MOV_W0_3),
    replacement = intArrayOf(MOV_W0_0),
)

private val PATCHES_BY_VERSION = mapOf(
    // "2.3.1" to listOf(
    //     invalidLicense(0x4632BF0),
    //     premiumAccess(0x480FE18),
    //     lockedItems(0x481038C),
    // ),
    // "2.4.0" to listOf(
    //     invalidLicense(0x4795E98),
    //     premiumAccess(0x497E5A8),
    //     lockedItems(0x497EB1C),
    // ),
    "2.5.0" to listOf(
        invalidLicense(0x4B45FA8),
        premiumAccess(0x4D2F934),
        lockedItems(0x4D2FEA8),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    execute {
        val version = packageMetadata.versionName
        val patches = PATCHES_BY_VERSION[version] ?: throw PatchException(
            "Sky Tonight version $version is not supported. " +
                    "Supported: ${PATCHES_BY_VERSION.keys.joinToString()}. " +
                    "Add offsets via Il2CppDumper (see KDoc).",
        )

        val so = get(LIBIL2CPP_PATH)
        if (!so.exists()) throw PatchException("$LIBIL2CPP_PATH not found in APK")

        val bytes = so.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        patches.forEach { p ->
            // Idempotency: if replacement bytes are already in place, skip.
            if (matches(buffer, p.offset, p.replacement)) return@forEach

            if (!matches(buffer, p.offset, p.expected)) {
                throw PatchException(
                    "${p.label} @ 0x${p.offset.toString(16).uppercase()} — " +
                            "expected bytes don't match; the offset table for v$version is stale",
                )
            }
            p.replacement.forEachIndexed { i, insn ->
                buffer.putInt(p.offset + i * 4, insn)
            }
        }

        so.writeBytes(bytes)
    }
}

private fun matches(buffer: ByteBuffer, offset: Int, words: IntArray): Boolean =
    words.withIndex().all { (i, w) -> buffer.getInt(offset + i * 4) == w }
