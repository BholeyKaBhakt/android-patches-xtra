package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary

/**
 * `libil2cpp.so` rewrites:
 *   - `GooglePlay.InvalidLicense()` → set `updateStopped` (+0x48) and return: no paywall / self-exit
 *   - `StoreInitializer.get_PremiumAccessEnabled()` → true (lock, ads and calendar gates all read it)
 *   - pre-2.5.3 only: `StoreInitializer.get_LockedItemsCount()` → 0. On 2.5.3 every reader checks
 *     premium first, so the count is never read; not re-proven on older builds, so kept there.
 */

internal const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

// ARM64 instruction encodings (little-endian)
private const val MOV_W0_3 = 0x52800060             // MOV W0, #3
private const val MOV_W8_1 = 0x52800028             // MOV W8, #1
private const val STRB_W8_X0_48 = 0x39012008        // STRB W8, [X0, #0x48]
private const val STP_X30_X19 = 0xA9BF4FFE.toInt()  // STP X30, X19, [SP, #-0x10]!
private const val MOV_X19_X0 = 0xAA0003F3.toInt()   // MOV X19, X0
private const val LDR_X8_X0_20 = 0xF9401008.toInt() // LDR X8, [X0, #0x20]

private fun invalidLicense(offset: Int) = BinaryPatch(
    "GooglePlay.InvalidLicense()", offset,
    expected = intArrayOf(STP_X30_X19, LDR_X8_X0_20, MOV_X19_X0),
    replacement = intArrayOf(MOV_W8_1, STRB_W8_X0_48, Arm64.RET),
)

private fun premiumAccess(offset: Int) = BinaryPatch(
    "StoreInitializer.get_PremiumAccessEnabled()", offset,
    expected = intArrayOf(STP_X30_X19, MOV_X19_X0),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

private fun lockedItems(offset: Int) = BinaryPatch(
    "StoreInitializer.get_LockedItemsCount()", offset,
    expected = intArrayOf(MOV_W0_3),
    replacement = intArrayOf(Arm64.MOV_W0_0),
)

internal val SKY_TONIGHT_PREMIUM_PATCHES = mapOf(
    "2.5.3" to listOf(
        invalidLicense(0x4BC9BB8),
        premiumAccess(0x4DC01E4),
    ),
    "2.5.0" to listOf(
        invalidLicense(0x4B45FA8),
        premiumAccess(0x4D2F934),
        lockedItems(0x4D2FEA8),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    execute {
        val version = packageMetadata.versionName
        val patches = SKY_TONIGHT_PREMIUM_PATCHES.forVersion("Sky Tonight", version, "Add offsets via Il2CppDumper (see KDoc).")
        patchNativeLibrary(LIBIL2CPP_PATH, "Sky Tonight", version, patches)
    }
}
