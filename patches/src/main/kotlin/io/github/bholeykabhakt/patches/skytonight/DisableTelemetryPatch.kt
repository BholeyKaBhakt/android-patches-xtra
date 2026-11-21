package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.telemetryHead
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * RET at the heads of `InitFirebaseAnalytics()` (Vito analytics dispatcher) and
 * `Vito.Service.Ads.AdsInitializer.Initialize()` (the UMP consent + ad-network chain; its only caller
 * is the store's ads init). The Android Firebase SDK still auto-collects via its init provider, hence
 * the manifest analytics dependency.
 */

internal val SKY_TONIGHT_TELEMETRY_SITES = mapOf(
    "2.6.0" to listOf(
        telemetryHead("InitFirebaseAnalytics", 0x2CFD02C),
        telemetryHead("AdsInitializer.Initialize", 0x2D402D8),
    ),
    "2.5.3" to listOf(
        telemetryHead("InitFirebaseAnalytics", 0x2B812F8),
        telemetryHead("AdsInitializer.Initialize", 0x2BC14B8),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    dependsOn(disableAnalyticsPatch)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Sky Tonight", SKY_TONIGHT_TELEMETRY_SITES)
    }
}
