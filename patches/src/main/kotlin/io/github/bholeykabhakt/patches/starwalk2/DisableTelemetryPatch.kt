package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary
import io.github.bholeykabhakt.patches.utils.retHead

/**
 * RET at the heads of `InitFirebaseAnalytics()` (Vito analytics dispatcher) and
 * `GoogleMobileAdsInitializer.Initialize()` (ads + UMP consent). The Android Firebase SDK still
 * auto-collects via its init provider, hence the manifest analytics dependency.
 */

// (name, file offset, prologue words); the 2.22.0 ads head is dump offset + 4 (padding).
internal val STAR_WALK_2_TELEMETRY_SITES = mapOf(
    "2.22.0" to listOf(
        retHead("InitFirebaseAnalytics", 0x25544BC, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        retHead("GoogleMobileAdsInitializer.Initialize", 0x259A070, 0xA9BD5FFE.toInt(), 0xA90157F6.toInt()),
    ),
    "2.20.3" to listOf(
        // STP X30,X21,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        retHead("InitFirebaseAnalytics", 0x2368C84, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        // STR X30,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        retHead("GoogleMobileAdsInitializer.Initialize", 0x23AC330, 0xF81E0FFE.toInt(), 0xA9014FF4.toInt()),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    dependsOn(disableAnalyticsPatch)

    execute {
        val version = packageMetadata.versionName
        val sites = STAR_WALK_2_TELEMETRY_SITES.forVersion("Star Walk 2", version, "Add offsets via Il2CppDumper.")
        patchNativeLibrary(LIBIL2CPP_PATH, "Star Walk 2", version, sites)
    }
}
