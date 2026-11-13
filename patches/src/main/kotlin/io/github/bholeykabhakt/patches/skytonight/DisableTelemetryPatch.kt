package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
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

// Sites: (name, dump.cs Offset, prologue words).
// Ads offset is +4 from the dumped 0x2B69874 — that word is alignment padding (a stray RET).
internal val SKY_TONIGHT_TELEMETRY_SITES = mapOf(
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
