package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.logMatchAll
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val noAnalyticsPatch = bytecodePatch(
    name = "Disable Logging(analytics) Patch",
) {
    compatibleWith(Compatibility(packageName = "org.zwanoo.android.speedtest"))

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
