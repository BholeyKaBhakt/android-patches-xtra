package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.telemetryHead
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * RET at the heads of `InitFirebaseAnalytics()` (Vito analytics dispatcher) and
 * `Vito.Service.Ads.AdsInitializer.Initialize()` (the UMP consent + ad-network chain; its only caller
 * is the store's ads init). The Android Firebase SDK still auto-collects via its init provider, hence
 * the manifest analytics dependency.
 */

internal val STAR_WALK_2_TELEMETRY_SITES = mapOf(
    "2.22.0" to listOf(
        telemetryHead("InitFirebaseAnalytics", 0x25544BC),
        telemetryHead("AdsInitializer.Initialize", 0x2597438),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    dependsOn(disableAnalyticsPatch)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Star Walk 2", STAR_WALK_2_TELEMETRY_SITES)
    }
}
