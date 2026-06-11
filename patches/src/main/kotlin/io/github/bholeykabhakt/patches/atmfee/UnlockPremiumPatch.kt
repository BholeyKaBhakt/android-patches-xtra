package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Unlocks ATM Fee Saver premium (removes ads, paywalls, and feature locks).
 *
 * Premium funnels through one chokepoint: `PremiumManager.isPremiumActive()`. ~40 call
 * sites (feature gates, ad insertion, paywall triggers) read it, and the backing field
 * is private, so forcing the getter to `return true` unlocks the whole app — independent
 * of what Google Play billing reports.
 *
 * Why the getter alone is enough: `BillingManager` writes premium via `setPremium(...)`
 * (storing `false` at startup when it finds no purchase), but that only updates the
 * private field + a `premiumState` StateFlow — it can't override the patched getter. The
 * StateFlow's observers merely *react to premium turning on* (dismiss paywall, drop ad
 * views, refresh chips, hide the converter overlay); with the getter already reporting
 * premium, none of that is load-bearing. Verified on device with billing disconnected
 * (so `setPremium` never ran): every gate still passed.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    execute {
        IsPremiumActiveFingerprint.logMatch.method.returnEarly(true)
    }
}
