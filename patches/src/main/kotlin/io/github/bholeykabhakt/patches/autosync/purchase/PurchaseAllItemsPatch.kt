package io.github.bholeykabhakt.patches.autosync.purchase

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val purchaseAllItemsPatch = bytecodePatch(
    name = "Purchase All Items",
) {
    compatibleWith(Compatibility(packageName = "com.ttxapps.autosync"))

    execute {
        IsAccountTypePurchasedFingerprint.logMatch.method.returnEarly(true)
    }
}