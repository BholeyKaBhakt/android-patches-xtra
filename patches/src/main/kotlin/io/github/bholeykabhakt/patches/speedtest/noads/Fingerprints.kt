package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.Opcode

/**
 * "Is this account ad-free?" check.
 *
 * Public package `com/ookla/speedtest/purchase/google/` is stable.
 * Method names rotate (v5 `BillingClientPurchaseManager.isAdFreeAccount`, v7 `D.b`),
 * but the body shape is invariant: load a data-store field, call a static utility,
 * then load the billing-client field, call a virtual on it. That's a 7-opcode prefix
 * unique to this method within the package.
 */
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
