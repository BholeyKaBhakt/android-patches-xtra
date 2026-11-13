package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SPEEDTEST
import io.github.bholeykabhakt.patches.utils.logMatchAll
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val noAnalyticsPatch = patch(
    name = "Disable Logging(analytics)",
) {
    compatibleWith(COMPATIBILITY_SPEEDTEST)

    execute {
        // info() + watch() share the same signature; patch both.
        LoggingStringVarargsFingerprint.logMatchAll.forEach {
            it.method.returnEarly()
        }
        LoggingAlarmFingerprint.logMatchAll.forEach {
            it.method.returnEarly()
        }
    }
}
