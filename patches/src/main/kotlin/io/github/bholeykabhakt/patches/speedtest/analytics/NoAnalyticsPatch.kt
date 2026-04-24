package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val noAnalyticsPatch = bytecodePatch(
    name = "Disable Logging(analytics) Patch",
) {
    compatibleWith("org.zwanoo.android.speedtest")

    execute {

        // don't send any logs
        loggingInfoFingerprint.method.returnEarly()
        loggingWatchFingerprint.method.returnEarly()
        loggingAlarmFingerprint.method.returnEarly()
    }
}