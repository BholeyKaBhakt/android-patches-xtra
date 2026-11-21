package io.github.bholeykabhakt.patches.solarwalk2

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLAR_WALK_2
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.telemetryHead
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion
import io.github.bholeykabhakt.patches.utils.retHead

/**
 * RET at the heads of the ad SDK initializers — `GoogleMobileAdsInitializer.Initialize()` (UMP consent +
 * Google Mobile Ads) and `UnityEngine.Advertisements.Advertisement.Initialize()` (Unity Ads; the ad center
 * builds its Unity interstitial unit even for premium users, and Unity Ads in turn wakes Google Mobile Ads
 * for bidding) — and of `InitFirebaseAnalytics()` (Vito analytics dispatcher). The Android Firebase SDK
 * still auto-collects via its init provider, hence the manifest analytics dependency.
 */

private const val STP_X30_X25_PRE = 0xA9BC67FE.toInt()  // STP X30, X25, [SP, #-0x40]!
private const val STP_X24_X23_SP10 = 0xA9015FF8.toInt() // STP X24, X23, [SP, #0x10]

internal val SOLAR_WALK_2_AD_SITES = mapOf(
    "1.6.9" to listOf(
        telemetryHead("GoogleMobileAdsInitializer.Initialize", 0x1C0F5D8),
        retHead("Advertisement.Initialize", 0x35E799C, STP_X30_X25_PRE, STP_X24_X23_SP10),
        telemetryHead("InitFirebaseAnalytics", 0x1BCFD70),
    ),
)

@Suppress("unused")
val removeAdsPatch = patch(
    name = "Remove Ads",
) {
    compatibleWith(COMPATIBILITY_SOLAR_WALK_2)

    dependsOn(disableAnalyticsPatch)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Solar Walk 2", SOLAR_WALK_2_AD_SITES)
    }
}
