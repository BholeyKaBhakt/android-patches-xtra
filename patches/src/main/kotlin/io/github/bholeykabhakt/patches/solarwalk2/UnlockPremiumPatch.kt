package io.github.bholeykabhakt.patches.solarwalk2

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLAR_WALK_2
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.STP_X30_X19_PRE
import io.github.bholeykabhakt.patches.shared.invalidLicense
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * `libil2cpp.so` rewrites:
 *   - `GooglePlay.InvalidLicense()` → set `updateStopped` and return: no paywall / self-exit
 *   - `StoreManager.get_UnlockedBase()` → true (base tier; feeds `Unlocker.BaseAccessEnabled`)
 *   - `StoreManager.get_UnlockedUnlimited()` → true (top tier; `SubscriptionIsActive` falls through to
 *     it and feeds `Unlocker.PremiumAccessEnabled`, which object locks, ads and calendar gates read)
 */

// ARM64 instruction encodings (little-endian)
private const val LDRB_W8_X0_30 = 0x3940C008            // LDRB W8, [X0, #0x30]
private const val CBZ_W8_PLUS_0C = 0x34000068           // CBZ W8, #+0xC
private const val LDR_W8_X0_34 = 0xB9403408.toInt()     // LDR W8, [X0, #0x34]

private fun unlockedBase(offset: Int) = BinaryPatch(
    "StoreManager.get_UnlockedBase()", offset,
    expected = intArrayOf(LDRB_W8_X0_30, CBZ_W8_PLUS_0C),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

private fun unlockedUnlimited(offset: Int) = BinaryPatch(
    "StoreManager.get_UnlockedUnlimited()", offset,
    expected = intArrayOf(STP_X30_X19_PRE, LDR_W8_X0_34),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

internal val SOLAR_WALK_2_PREMIUM_PATCHES = mapOf(
    "1.6.9" to listOf(
        invalidLicense(0x2E15E04),
        unlockedBase(0x19C1D9C),
        unlockedUnlimited(0x19C1D4C),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_SOLAR_WALK_2)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Solar Walk 2", SOLAR_WALK_2_PREMIUM_PATCHES)
    }
}
