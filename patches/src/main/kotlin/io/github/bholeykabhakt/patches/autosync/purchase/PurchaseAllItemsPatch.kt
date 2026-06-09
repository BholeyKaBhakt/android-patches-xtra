package io.github.bholeykabhakt.patches.autosync.purchase

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.autosync.integrity.forceIntegrityStatePatch
import io.github.bholeykabhakt.patches.autosync.pairip.disablePairIpPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val purchaseAllItemsPatch = bytecodePatch(
    name = "Purchase All Items",
) {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    // Required for the bypass to survive: PairIP keeps the license-check intent
    // alive, and the integrity / signer-hash short-circuits keep tamper-detection
    // from rolling features back. Both are nameless so users see this as one patch.
    dependsOn(disablePairIpPatch, forceIntegrityStatePatch)

    execute {
        IsAccountTypePurchasedFingerprint.logMatch.method.returnEarly(true)
    }
}
