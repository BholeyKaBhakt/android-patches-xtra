package io.github.bholeykabhakt.patches.autosync.purchase

import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bholeykabhakt.patches.utils.returnEarly

internal val isAccountTypePurchasedFingerprint = fingerprint {
    returns("Z")
    parameters("Ljava/lang/String;")
    strings("accountType")
    opcodes(
        Opcode.INVOKE_DIRECT,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST_4,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT
    )
    custom { _, classDef ->
        classDef.equals("Lcom/ttxapps/autosync/iab/LicenseManager;")
    }
}

@Suppress("unused")
val purchaseAllItemsPatch = bytecodePatch(
    name = "Purchase All Items",
) {
    compatibleWith("com.ttxapps.autosync")

    execute {
        isAccountTypePurchasedFingerprint.method.returnEarly("0x1")
    }
}