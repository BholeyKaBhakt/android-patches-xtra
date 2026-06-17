package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Unlocks ATM Fee Saver premium (removes ads, paywalls, feature locks) by forcing the single
 * chokepoint getter `PremiumManager.isPremiumActive()` to return true. Pulls in
 * [disableAnalyticsPatch] to turn off Firebase Analytics / Crashlytics collection.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    dependsOn(disableAnalyticsPatch)

    execute {
        IsPremiumActiveFingerprint.logMatch.method.returnEarly(true)
    }
}
