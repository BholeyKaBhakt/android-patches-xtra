package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/** Forces `PremiumManager.isPremiumActive()` true. */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    dependsOn(disableAnalyticsPatch)

    execute {
        IsPremiumActiveFingerprint.logMatch.method.returnEarly(true)
    }
}
