package io.github.bholeykabhakt.patches.blockerx

import app.morphe.patcher.Fingerprint

/** `CommonUtils.redirectFromAcceptTremToNextScreen(Context)`: the post-splash router (onboarding / sign-in / home). */
internal object RedirectToNextScreenFingerprint : Fingerprint(
    definingClass = "Lio/funswitch/blocker/utils/CommonUtils;",
    name = "redirectFromAcceptTremToNextScreen",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)
