package io.github.bholeykabhakt.patches.autosync

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

// Forces the "is purchased" gate true.
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    dependsOn(dePairIpVmPatch, forceIntegrityStatePatch)

    execute {
        IsAccountTypePurchasedFingerprint.logMatch.method.returnEarly(true)
    }
}
