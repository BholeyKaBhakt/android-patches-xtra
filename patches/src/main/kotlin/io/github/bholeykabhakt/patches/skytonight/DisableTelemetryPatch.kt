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
 * `GoogleMobileAdsInitializer.Initialize()` (ads + UMP consent). The Android Firebase SDK still
 * auto-collects via its init provider, hence the manifest analytics dependency.
 */

// (name, file offset, prologue words); the ads head is dump offset + 4 (padding).
internal val SKY_TONIGHT_TELEMETRY_SITES = mapOf(
    "2.5.3" to listOf(
        retHead("InitFirebaseAnalytics", 0x2B812F8, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        retHead("GoogleMobileAdsInitializer.Initialize", 0x2BC4008, 0xA9BD5FFE.toInt(), 0xA90157F6.toInt()),
    ),
    "2.5.0" to listOf(
        // STP X30,X21,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        retHead("InitFirebaseAnalytics", 0x2B27208, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        // STP X30,X23,[SP,#-0x30]! ; STP X22,X21,[SP,#0x10]
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
