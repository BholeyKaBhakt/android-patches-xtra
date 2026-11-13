package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary
import io.github.bholeykabhakt.patches.utils.retHead

/**
 * NOP-RETs two `libil2cpp.so` inits so telemetry and ads never start: `InitFirebaseAnalytics()`
 * (the Vito analytics dispatcher) and `GoogleMobileAdsInitializer.Initialize()` (Google Mobile Ads
 * + UMP consent). A bare `RET` at each head is safe — see [retHead].
 *
 * The NOP-RET only stops the Unity dispatcher; the bundled Android Firebase SDK still auto-collects
 * via `FirebaseInitProvider`, so this `dependsOn` [disableAnalyticsPatch] to disable collection via
 * the manifest flags too.
 */

// Sites: (name, dump.cs Offset, prologue words). Both offsets are exact.
internal val STAR_WALK_2_TELEMETRY_SITES = mapOf(
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
