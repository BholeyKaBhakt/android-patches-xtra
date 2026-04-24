package io.github.bholeykabhakt.patches.automate

import app.morphe.patcher.fingerprint

internal val isBlockLimitReachedFingerPrint = fingerprint {
    returns("Z")
    strings("checkPremiumAllow", "runningStatementCount", "count")
//    opcodes(
//        Opcode.CONST_STRING,
//        Opcode.INVOKE_VIRTUAL,
//        Opcode.MOVE_RESULT_WIDE,
//        Opcode.CONST_WIDE_16,
//        Opcode.CMP_LONG,
//        Opcode.IF_LTZ,
//        Opcode.RETURN
//    )
//    custom { method, _ ->
//        method.definingClass == "Lcom/llamalab/automate/AutomateService;"
//    }
}