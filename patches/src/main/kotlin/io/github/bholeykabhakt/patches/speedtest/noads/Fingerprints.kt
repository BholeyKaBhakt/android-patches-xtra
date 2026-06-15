package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

/** The "is this account ad-free?" check, in `com/ookla/speedtest/purchase/google/`. */
internal object IsAdFreeAccountFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/speedtest/purchase/google/",
    returnType = "Z",
    parameters = emptyList(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT,
        Opcode.IGET_OBJECT,
        Opcode.IGET_OBJECT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
    ),
)
