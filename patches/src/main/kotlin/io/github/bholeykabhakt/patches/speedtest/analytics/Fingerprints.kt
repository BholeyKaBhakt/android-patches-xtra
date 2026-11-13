package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private val publicStaticFinal = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL)

/** Logger registration of the DevMetrics bus (`com/ookla/tools/logging/`): `?(O2DevMetricsLogger)V`. */
internal object RegisterDevMetricsLoggerFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/",
    accessFlags = publicStaticFinal,
    returnType = "V",
    parameters = listOf("Lcom/ookla/tools/logging/O2DevMetricsLogger;"),
)

/** Logger registration of the event bus: `?(O2EventLogger)V` (Firebase Analytics events go here). */
internal object RegisterEventLoggerFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/",
    accessFlags = publicStaticFinal,
    returnType = "V",
    parameters = listOf("Lcom/ookla/tools/logging/O2EventLogger;"),
)
