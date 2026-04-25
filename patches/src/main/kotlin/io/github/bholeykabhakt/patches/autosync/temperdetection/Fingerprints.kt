package io.github.bholeykabhakt.patches.autosync.temperdetection

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

internal object TemperDetectionVarHGetterFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/sync/",
    name = "f",
    returnType = "Z",
    parameters = emptyList(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT,
        Opcode.RETURN,
    ),
    custom = { _, classDef ->
        classDef.type.endsWith("a;")
    },
)

internal object TemperDetectionVarZGetterFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/sync/",
    name = "z",
    returnType = "Z",
    parameters = emptyList(),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IGET_BOOLEAN,
        Opcode.RETURN,
    ),
    custom = { _, classDef ->
        classDef.fields.any {
            it.name == "a" && it.type == "Z"
        }
    },
)