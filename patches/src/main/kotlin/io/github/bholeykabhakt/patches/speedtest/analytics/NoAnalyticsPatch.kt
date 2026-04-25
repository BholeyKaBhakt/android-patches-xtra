package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val noAnalyticsPatch = bytecodePatch(
    name = "Disable Logging(analytics) Patch",
) {
    compatibleWith(Compatibility(packageName = "org.zwanoo.android.speedtest"))

    execute {

        // don't send any logs
        LoggingInfoFingerprint.method.returnEarly()
        LoggingWatchFingerprint.method.returnEarly()
        LoggingAlarmFingerprint.method.returnEarly()
    }
}