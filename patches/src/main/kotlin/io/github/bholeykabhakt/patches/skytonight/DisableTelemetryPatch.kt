package io.github.bholeykabhakt.patches.skytonight

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SKY_TONIGHT
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stops Sky Tonight's in-app telemetry at the source by NOP-ing
 * `Vito.Metrics.Analytics.InitFirebaseAnalytics()` in `libil2cpp.so`.
 *
 * The Firebase Analytics SDK itself is already deactivated by the universal
 * [io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch]
 * (which sets the `firebase_analytics_collection_deactivated` manifest flag).
 * But Vito wraps Firebase in their own `FirebaseAnalyticsComponent` and the
 * app still calls `Analytics.LogEvent(...)` → `FirebaseAnalyticsComponent.LogEvent(...)`
 * at runtime — which logs noisy `parameter value is too long` warnings even
 * when the underlying SDK drops the call. This patch short-circuits the whole
 * pipeline by making `InitFirebaseAnalytics()` return immediately, so the
 * `FirebaseAnalyticsComponent` is never registered with the analytics manager
 * and downstream `LogEvent` calls become no-ops at the dispatcher layer.
 *
 * Patch: write `RET` (`0xD65F03C0`) at the function head — no callee-saved
 * registers were modified yet, no stack adjustment to unwind.
 */

private const val RET = 0xD65F03C0.toInt()
private const val STP_X30_X21_PRE = 0xA9BE57FE.toInt() // STP X30, X21, [SP, #-0x20]!
private const val STP_X20_X19 = 0xA9014FF4.toInt()     // STP X20, X19, [SP, #0x10]

private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

// Per-version offsets of `Vito.Metrics.Analytics.InitFirebaseAnalytics()` as
// reported by Il2CppDumper (`dump.cs` Offset: column).
private val INIT_FIREBASE_ANALYTICS_OFFSETS = mapOf(
    // "2.4.0" to 0x28E2C50,
    "2.5.0" to 0x2B27208,
)

@Suppress("unused")
val disableTelemetryPatch = rawResourcePatch(
    name = "Disable In-App Telemetry",
    default = false,
) {
    compatibleWith(COMPATIBILITY_SKY_TONIGHT)

    execute {
        val version = packageMetadata.versionName
        val offset = INIT_FIREBASE_ANALYTICS_OFFSETS[version] ?: throw PatchException(
            "Sky Tonight version $version is not supported. " +
                    "Supported: ${INIT_FIREBASE_ANALYTICS_OFFSETS.keys.joinToString()}.",
        )

        val so = get(LIBIL2CPP_PATH)
        if (!so.exists()) throw PatchException("$LIBIL2CPP_PATH not found in APK")

        val bytes = so.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Idempotent.
        if (buffer.getInt(offset) == RET) return@execute

        // Assert the prologue pair to guard against an outdated offset table.
        val first = buffer.getInt(offset)
        val second = buffer.getInt(offset + 4)
        if (first != STP_X30_X21_PRE || second != STP_X20_X19) {
            throw PatchException(
                "InitFirebaseAnalytics() @ 0x${offset.toString(16).uppercase()} — " +
                        "expected STP X30,X21 ; STP X20,X19 prologue, got " +
                        "0x${first.toUInt().toString(16)} 0x${second.toUInt().toString(16)}; " +
                        "v$version offset table is stale",
            )
        }

        buffer.putInt(offset, RET)
        so.writeBytes(bytes)
    }
}
