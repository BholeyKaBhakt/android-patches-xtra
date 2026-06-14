package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Makes the patched build **connect to a VPN server** by defeating vpnify's server-side
 * signature pinning.
 *
 * `openvpn/getserver` serves the real OpenVPN config only when the request's `"nocache"` field
 * equals the official signing-cert fingerprint — `vf.e.c()` = `hex(SHA1(signatures[0]))[:10]`.
 * Any re-signed build computes a different hash → sabotaged config (`127.0.0.1`) + a popup. We
 * force `vf.e.c()` to return the official value, which also satisfies the `yf.a` client-side
 * check that compares the same hash.
 *
 * [OFFICIAL_SIG_HASH] only changes if vpnify rotates its signing key (effectively never). To
 * refresh it: read the `nocache` field the **official** Play build sends to `openvpn/getserver`,
 * or compute `hex(SHA1(signingCert))[:10]` from the official APK's certificate.
 */
@Suppress("unused")
val spoofSignatureHashPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_VPNIFY)

    execute {
        SignatureHashFingerprint.logMatch.method.returnEarly(OFFICIAL_SIG_HASH)
    }
}

/** SHA1 fingerprint of vpnify's official signing cert, as the app's 10-char `nocache` token. */
private const val OFFICIAL_SIG_HASH = "8a644bcbd9"
