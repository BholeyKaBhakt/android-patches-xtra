package io.github.bholeykabhakt.patches.speedtest.analytics

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The Ookla DevMetrics dispatcher class lives in `com/ookla/tools/logging/`. The class name
 * rotates (v5 `O2DevMetrics`, v7 `a`) and the method names rotate too (v5 `info/watch/alarm`,
 * v7 `l/w/c`), but the parameter shapes and the `public static final varargs` modifier are
 * invariant — these are the only varargs sinks for the analytics pipeline.
 *
 * Anchor by signature shape + package + accessFlags. Multiple methods match (info-varargs and
 * watch-varargs share the same signature in both versions); patch them all via [logMatchAll].
 */

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
