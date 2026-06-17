package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
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

// Sites: (name, dump.cs Offset, prologue word 0, prologue word 1). Both offsets are exact.
private val SITES_BY_VERSION = mapOf(
    "2.20.3" to listOf(
        // STP X30,X21,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        RetSite("InitFirebaseAnalytics", 0x2368C84, 0xA9BE57FE.toInt(), 0xA9014FF4.toInt()),
        // STR X30,[SP,#-0x20]! ; STP X20,X19,[SP,#0x10]
        RetSite(
            "GoogleMobileAdsInitializer.Initialize",
            0x23AC330,
            0xF81E0FFE.toInt(),
            0xA9014FF4.toInt()
        ),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = rawResourcePatch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    dependsOn(disableAnalyticsPatch)

    execute {
        val version = packageMetadata.versionName
        val sites = SITES_BY_VERSION[version] ?: throw PatchException(
            "Star Walk 2 version $version is not supported. " +
                    "Supported: ${SITES_BY_VERSION.keys.joinToString()}.",
        )
        get(LIBIL2CPP_PATH).writeRetHeads("Star Walk 2", version, sites)
    }
}
