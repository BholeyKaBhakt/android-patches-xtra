package io.github.bholeykabhakt.patches.solidexplorer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLID_EXPLORER
import io.github.bholeykabhakt.patches.utils.logMatch

/**
 * Disables Solid Explorer's Firebase Analytics ("Send usage statistics") and Crashlytics. The
 * manifest dependency hard-deactivates Analytics; forcing the runtime switch
 * `SEApp.setAnalyticsEnabled(false)` is what also stops Crashlytics collection.
 */
@Suppress("unused")
val disableTelemetryPatch = bytecodePatch(
    name = "Disable Telemetry",
) {
    compatibleWith(COMPATIBILITY_SOLID_EXPLORER)

    dependsOn(disableAnalyticsPatch)

    execute {
        SetAnalyticsEnabledFingerprint.logMatch.method.addInstructions(0, "const/4 p0, 0x0")
    }
}
