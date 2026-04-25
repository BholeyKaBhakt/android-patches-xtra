package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.Fingerprint

// Lcom/ookla/tools/logging/O2DevMetrics;->info(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V
internal object LoggingInfoFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/O2DevMetrics;",
    name = "info",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;",
    ),
)

// Lcom/ookla/tools/logging/O2DevMetrics;->watch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V
internal object LoggingWatchFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/O2DevMetrics;",
    name = "watch",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;",
    ),
)

// Lcom/ookla/tools/logging/O2DevMetrics;->alarm(Ljava/lang/Throwable;[Ljava/lang/String;)V
internal object LoggingAlarmFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/O2DevMetrics;",
    name = "alarm",
    returnType = "V",
    parameters = listOf("Ljava/lang/Throwable;", "[Ljava/lang/String;"),
)