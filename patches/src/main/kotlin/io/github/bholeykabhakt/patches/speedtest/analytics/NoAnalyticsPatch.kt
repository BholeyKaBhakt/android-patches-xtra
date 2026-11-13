package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SPEEDTEST
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Both Ookla logging buses (DevMetrics + events) dispatch to whatever loggers are registered at
 * startup (Firebase Analytics, Crashlytics, logcat); dropping the registrations leaves them empty.
 * The manifest dependency stops the Firebase SDK's own auto-collection.
 */
@Suppress("unused")
val noAnalyticsPatch = patch(
    name = "Disable Logging(analytics)",
) {
    compatibleWith(COMPATIBILITY_SPEEDTEST)

    dependsOn(disableAnalyticsPatch)

    execute {
        RegisterDevMetricsLoggerFingerprint.logMatch.method.returnEarly()
        RegisterEventLoggerFingerprint.logMatch.method.returnEarly()
    }
}
