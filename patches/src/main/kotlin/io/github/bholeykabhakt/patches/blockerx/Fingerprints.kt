package io.github.bholeykabhakt.patches.blockerx

import app.morphe.patcher.Fingerprint

/**
 * `CommonUtils.redirectFromAcceptTremToNextScreen(Context)` — the central post-splash router that
 * picks onboarding / sign-in / permissions / home. Forcing it straight to home is what lets the
 * re-signed build reach MainActivity (the stock onboarding dead-ends at the intro upsell, whose ✕
 * doesn't advance). The entitlement flag getters are patched by name on `BlockerXAppSharePref` in
 * [unlockPremiumPatch], so they need no fingerprints.
 */
internal object RedirectToNextScreenFingerprint : Fingerprint(
    definingClass = "Lio/funswitch/blocker/utils/CommonUtils;",
    name = "redirectFromAcceptTremToNextScreen",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)
