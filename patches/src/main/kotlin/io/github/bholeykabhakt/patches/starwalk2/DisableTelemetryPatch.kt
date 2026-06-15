package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes `RET` at the head of `Vito.Metrics.Analytics.InitFirebaseAnalytics()` so the in-app
 * analytics dispatcher is never initialised. Offset is per-version; the prologue is asserted.
 */

private const val RET = 0xD65F03C0.toInt()
private const val STP_X30_X21_PRE = 0xA9BE57FE.toInt() // STP X30, X21, [SP, #-0x20]!
private const val STP_X20_X19 = 0xA9014FF4.toInt()     // STP X20, X19, [SP, #0x10]

private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

private val INIT_FIREBASE_ANALYTICS_OFFSETS = mapOf(
    "2.20.3" to 0x2368C84,
)

@Suppress("unused")
val disableTelemetryPatch = rawResourcePatch(
    name = "Disable In-App Telemetry",
    default = false,
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    execute {
        val version = packageMetadata.versionName
        val offset = INIT_FIREBASE_ANALYTICS_OFFSETS[version] ?: throw PatchException(
            "Star Walk 2 version $version is not supported. " +
                    "Supported: ${INIT_FIREBASE_ANALYTICS_OFFSETS.keys.joinToString()}.",
        )

        val so = get(LIBIL2CPP_PATH)
        if (!so.exists()) throw PatchException("$LIBIL2CPP_PATH not found in APK")

        val bytes = so.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (buffer.getInt(offset) == RET) return@execute

        val first = buffer.getInt(offset)
        val second = buffer.getInt(offset + 4)
        if (first != STP_X30_X21_PRE || second != STP_X20_X19) {
            throw PatchException(
                "InitFirebaseAnalytics() @ 0x${offset.toString(16).uppercase()} — " +
                        "expected STP X30,X21 ; STP X20,X19 prologue, got " +
                        "0x${first.toUInt().toString(16)} 0x${second.toUInt().toString(16)}",
            )
        }

        buffer.putInt(offset, RET)
        so.writeBytes(bytes)
    }
}
