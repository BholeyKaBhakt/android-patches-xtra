package io.github.bholeykabhakt.patches.cosmicwatch

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_COSMIC_WATCH
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion
import io.github.bholeykabhakt.patches.utils.retHead

/**
 * Matomo in-app analytics off: `AnalyticsService.CanTrack()` → false (nothing is queued) and RET at
 * `AnalyticsService.Flush()`, which sends the session batch (incl. a page view) without checking it.
 */

internal val COSMIC_WATCH_TELEMETRY_SITES = mapOf(
    "3.0.1" to listOf(
        BinaryPatch(
            "AnalyticsService.CanTrack()", 0x164EB28,
            expected = intArrayOf(PACIASP, STR_X30_SP_M20),
            replacement = intArrayOf(Arm64.MOV_W0_0, Arm64.RET),
        ),
        retHead("AnalyticsService.Flush()", 0x164E8FC, PACIASP, STR_X30_SP_M20),
    ),
)

@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable In-App Telemetry",
) {
    compatibleWith(COMPATIBILITY_COSMIC_WATCH)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Cosmic Watch", COSMIC_WATCH_TELEMETRY_SITES)
    }
}
