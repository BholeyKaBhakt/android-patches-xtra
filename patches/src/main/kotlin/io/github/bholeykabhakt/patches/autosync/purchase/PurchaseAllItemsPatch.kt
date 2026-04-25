package io.github.bholeykabhakt.patches.autosync.purchase

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

internal object IsAccountTypePurchasedFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/iab/LicenseManager;",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_DIRECT,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST_4,
        Opcode.INVOKE_INTERFACE,
        Opcode.MOVE_RESULT,
    ),
    strings = listOf("accountType"),
)

@Suppress("unused")
val purchaseAllItemsPatch = bytecodePatch(
    name = "Purchase All Items",
) {
    compatibleWith(Compatibility(packageName = "com.ttxapps.autosync"))

    execute {
        IsAccountTypePurchasedFingerprint.logMatch.method.returnEarly("0x1")
    }
}