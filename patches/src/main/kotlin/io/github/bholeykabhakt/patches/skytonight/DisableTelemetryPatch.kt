package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import io.github.bholeykabhakt.patches.utils.RetSite
import io.github.bholeykabhakt.patches.utils.writeRetHeads

/**
 * NOP-RETs two `libil2cpp.so` inits so telemetry and ads never start: `InitFirebaseAnalytics()`
 * (the Vito analytics dispatcher) and `GoogleMobileAdsInitializer.Initialize()` (Google Mobile Ads
 * + UMP consent). A bare `RET` at each head is safe — see [writeRetHeads].
 *
 * The NOP-RET only stops the Unity dispatcher; the bundled Android Firebase SDK still auto-collects
 * via `FirebaseInitProvider`, so this `dependsOn` [disableAnalyticsPatch] to disable collection via
 * the manifest flags too.
 */

private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

// Sites: (name, dump.cs Offset, prologue word 0, prologue word 1).
// Ads offset is +4 from the dumped 0x2B69874 — that word is alignment padding (a stray RET).
private val SITES_BY_VERSION = mapOf(
    "2.5.0" to listOf(
        // STP X30,X21,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        RetSite("InitFirebaseAnalytics", 0x2B27208, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        // STP X30,X23,[SP,#-0x30]! ; STP X22,X21,[SP,#0x10]
        RetSite(
            "GoogleMobileAdsInitializer.Initialize",
            0x2B69878,
            0xA9BD5FFE.toInt(),
            0xA90157F6.toInt()
        ),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = rawResourcePatch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    dependsOn(disableAnalyticsPatch)

    execute {
        val version = packageMetadata.versionName
        val sites = SITES_BY_VERSION[version] ?: throw PatchException(
            "Sky Tonight version $version is not supported. " +
                    "Supported: ${SITES_BY_VERSION.keys.joinToString()}.",
        )
        get(LIBIL2CPP_PATH).writeRetHeads("Sky Tonight", version, sites)
    }
}
