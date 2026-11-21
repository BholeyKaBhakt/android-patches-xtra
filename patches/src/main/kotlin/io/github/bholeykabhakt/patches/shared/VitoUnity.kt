package io.github.bholeykabhakt.patches.shared

import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.retHead

/** `libil2cpp.so` sites shared by Vito Technology's Unity apps (Sky Tonight, Star Walk 2, Solar Walk 2). */

internal const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

// ARM64 instruction encodings (little-endian)
internal const val STP_X30_X19_PRE = 0xA9BF4FFE.toInt()  // STP X30, X19, [SP, #-0x10]!
internal const val STP_X30_X21_PRE = 0xA9BE57FE.toInt()  // STP X30, X21, [SP, #-0x20]!
internal const val STP_X20_X19_SP10 = 0xA9014FF4.toInt() // STP X20, X19, [SP, #0x10]
internal const val MOV_X19_X0 = 0xAA0003F3.toInt()       // MOV X19, X0
internal const val MOV_W8_1 = 0x52800028                 // MOV W8, #1
private const val LDR_X8_X0_20 = 0xF9401008.toInt()      // LDR X8, [X0, #0x20]
private const val STRB_W8_X0_48 = 0x39012008             // STRB W8, [X0, #0x48]

/** `GooglePlay.InvalidLicense()` → set `updateStopped` (+0x48) and return: no paywall / self-exit. */
internal fun invalidLicense(offset: Int) = BinaryPatch(
    "GooglePlay.InvalidLicense()", offset,
    expected = intArrayOf(STP_X30_X19_PRE, LDR_X8_X0_20, MOV_X19_X0),
    replacement = intArrayOf(MOV_W8_1, STRB_W8_X0_48, Arm64.RET),
)

/** RET at the head of a telemetry init (`InitFirebaseAnalytics`, `AdsInitializer.Initialize`). */
internal fun telemetryHead(label: String, offset: Int) =
    retHead(label, offset, STP_X30_X21_PRE, STP_X20_X19_SP10)
