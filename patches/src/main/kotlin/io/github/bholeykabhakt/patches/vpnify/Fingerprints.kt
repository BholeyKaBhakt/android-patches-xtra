package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.Fingerprint

/** Fingerprints for the premium getters / signature-hash getter. See [unlockPremiumPatch]. */

/** `vf.d.f()` — "is subscription active"; the premium chokepoint (~32 callers). */
internal object SubscriptionActiveFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("subscription_active"),
)

/** `vf.d.e()` — "is auto-renewing subscription" (auxiliary; 2 callers). */
internal object AutoRenewingSubscriptionFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("is_auto_renewing_subscription"),
)

/**
 * `vf.e.c(Context)` — computes the signing-cert fingerprint `hex(SHA1(signatures[0]))[:10]`, sent
 * as the `"nocache"` field that gates `openvpn/getserver` (see [spoofSignatureHashPatch]). Anchored
 * on the stable crypto literals `"X509"` + `"SHA1"` in a `String`-returning single-`Context` method.
 */
internal object SignatureHashFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("X509", "SHA1"),
)
