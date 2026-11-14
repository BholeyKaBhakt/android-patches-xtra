package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Forces `PremiumManager.isPremiumActive()` true (every ad/feature gate reads it), and skips the ad
 * consent form + AdMob init that `HomeActivity` otherwise runs regardless of premium.
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    dependsOn(disableAnalyticsPatch)

    execute {
        IsPremiumActiveFingerprint.logMatch.method.returnEarly(true)

        // Not initConsentSettings(): it sets the lateinit ConsentInformation the options menu reads.
        mutableClassDefBy(HOME_ACTIVITY).methods
            .filter { it.name == "requestConsentInfo" || it.name == "initializeMobileAdsSdk" }
            .also { if (it.size != 2) throw PatchException("Consent/ads init methods not found") }
            .forEach { it.returnEarly() }
    }
}
