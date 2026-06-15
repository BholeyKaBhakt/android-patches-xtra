package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/** The varargs sinks of the Ookla DevMetrics dispatcher in `com/ookla/tools/logging/`. */

private val publicStaticVarargs = listOf(
    AccessFlags.PUBLIC,
    AccessFlags.STATIC,
    AccessFlags.FINAL,
    AccessFlags.VARARGS,
)

/** Matches `info(String,String,String,[String])V` AND `watch(String,String,String,[String])V`. */
internal object LoggingStringVarargsFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/",
    accessFlags = publicStaticVarargs,
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;",
    ),
)

/** Matches `alarm(Throwable,[String])V`. */
internal object LoggingAlarmFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/tools/logging/",
    accessFlags = publicStaticVarargs,
    returnType = "V",
    parameters = listOf("Ljava/lang/Throwable;", "[Ljava/lang/String;"),
)
