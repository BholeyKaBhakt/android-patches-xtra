package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.MOV_X19_X0
import io.github.bholeykabhakt.patches.shared.STP_X30_X19_PRE
import io.github.bholeykabhakt.patches.shared.invalidLicense
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * `libil2cpp.so` rewrites:
 *   - `GooglePlay.InvalidLicense()` → set `updateStopped` and return: no paywall / self-exit
 *   - `StoreInitializer.get_PremiumAccessEnabled()` → true (lock, ads and calendar gates all read it)
 */

private fun premiumAccess(offset: Int) = BinaryPatch(
    "StoreInitializer.get_PremiumAccessEnabled()", offset,
    expected = intArrayOf(STP_X30_X19_PRE, MOV_X19_X0),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

internal val SKY_TONIGHT_PREMIUM_PATCHES = mapOf(
    "2.6.0" to listOf(
        invalidLicense(0x4E57CA8),
        premiumAccess(0x505B90C),
    ),
    "2.5.3" to listOf(
        invalidLicense(0x4BC9BB8),
        premiumAccess(0x4DC01E4),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Sky Tonight", SKY_TONIGHT_PREMIUM_PATCHES)
    }
}
