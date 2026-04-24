package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.fingerprint

// Lcom/ookla/speedtest/analytics/google/BillingClientPurchaseManager;->isAdFreeAccount()Z
internal val loggingInfoFingerprint = fingerprint {
    returns("V")
    parameters(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;"
    )
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/tools/logging/O2DevMetrics;") && method.name == "info"
    }
}

// Lcom/ookla/tools/logging/O2DevMetrics;->watch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V
internal val loggingWatchFingerprint = fingerprint {
    returns("V")
    parameters(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;"
    )
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/tools/logging/O2DevMetrics;") && method.name == "watch"
    }
}

// Lcom/ookla/tools/logging/O2DevMetrics;->alarm(Ljava/lang/Throwable;[Ljava/lang/String;)V
internal val loggingAlarmFingerprint = fingerprint {
    returns("V")
    parameters("Ljava/lang/Throwable;", "[Ljava/lang/String;")
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/tools/logging/O2DevMetrics;") && method.name == "alarm"
    }
}

// Lcom/ookla/mobile4/app/logging/LoggingInitializer;->initialize(Landroid/content/Context;)V
//internal val loggingInitializerFingerprint = fingerprint {
//    returns("V")
//    parameters("Landroid/content/Context;")
//    custom { method, classDef ->
//        classDef.equals("Lcom/ookla/mobile4/app/logging/LoggingInitializer;") && method.name == "initialize"
//    }
//}

// Lcom/ookla/speedtest/app/CrashlyticsManager;->initialize()V
//internal val crashlyticsManagerFingerprint = fingerprint {
//    returns("V")
//    parameters()
//    custom { method, classDef ->
//        classDef.equals("Lcom/ookla/speedtest/app/CrashlyticsManager;") && method.name == "initialize"
//    }
//}