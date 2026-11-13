package io.github.bholeykabhakt.patches.solidexplorer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLID_EXPLORER
import io.github.bholeykabhakt.patches.utils.logMatch

/**
 * Turns off Firebase Analytics and Crashlytics: the manifest dependency deactivates Analytics, and
 * `SEApp.setAnalyticsEnabled(false)` is what also stops Crashlytics.
 */
@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable Telemetry",
) {
    compatibleWith(COMPATIBILITY_SOLID_EXPLORER)

    dependsOn(disableAnalyticsPatch)

    execute {
        SetAnalyticsEnabledFingerprint.logMatch.method.addInstructions(0, "const/4 p0, 0x0")
    }
}
