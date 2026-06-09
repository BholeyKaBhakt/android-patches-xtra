package io.github.bholeykabhakt.patches.autosync.purchase

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

/**
 * Main license check (purchase gate) for premium status (changed since v7.4+)
 *
 * This is the 1st check everywhere and checks for real `.License` string.
 * Other methods in `LicenseManager` are either for Trial check or Legacy ultimate check.
 * These methods also call this method first and if `true` they return.
 */
internal object IsAccountTypePurchasedFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/iab/LicenseManager;",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(".License"), // str.concat(".License") — product naming convention, stable
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.INVOKE_INTERFACE, // SharedPreferences.getBoolean(key, false)
        Opcode.MOVE_RESULT, // z2 = result
        Opcode.SGET_OBJECT, // ic8.b (the product bitmask HashMap)
        Opcode.INVOKE_VIRTUAL, // HashMap.get(str)
        Opcode.MOVE_RESULT_OBJECT, // num (Integer or null)
        Opcode.CHECK_CAST, // (Integer) num
    ),
)
