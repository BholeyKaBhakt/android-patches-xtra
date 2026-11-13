package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary
import io.github.bholeykabhakt.patches.utils.retHead

/**
 * RET at the heads of `InitFirebaseAnalytics()` (Vito analytics dispatcher) and
 * `Vito.Service.Ads.AdsInitializer.Initialize()` (the UMP consent + ad-network chain; its only caller
 * is the store's ads init). The Android Firebase SDK still auto-collects via its init provider, hence
 * the manifest analytics dependency.
 */

// (name, file offset, prologue words). InitFirebaseAnalytics / AdsInitializer heads:
// STP X30,X21,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
internal val SKY_TONIGHT_TELEMETRY_SITES = mapOf(
    "2.5.3" to listOf(
        retHead("InitFirebaseAnalytics", 0x2B812F8, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        retHead("AdsInitializer.Initialize", 0x2BC14B8, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
    ),
    "2.5.0" to listOf(
        retHead("InitFirebaseAnalytics", 0x2B27208, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        retHead("AdsInitializer.Initialize", 0x2B66BF8, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        // Pre-2.5.3 site, kept (not proven dead on this build): GoogleMobileAdsInitializer.Initialize
        retHead("GoogleMobileAdsInitializer.Initialize", 0x2B69878, 0xA9BD5FFE.toInt(), 0xA90157F6.toInt()),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    dependsOn(disableAnalyticsPatch)

    execute {
        val version = packageMetadata.versionName
        val sites = SKY_TONIGHT_TELEMETRY_SITES.forVersion("Sky Tonight", version, "Add offsets via Il2CppDumper.")
        patchNativeLibrary(LIBIL2CPP_PATH, "Sky Tonight", version, sites)
    }
}
