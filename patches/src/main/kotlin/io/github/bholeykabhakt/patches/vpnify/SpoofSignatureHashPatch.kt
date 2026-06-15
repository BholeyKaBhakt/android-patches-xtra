package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Lets the patched build connect to a VPN server: a re-signed build otherwise gets a sabotaged
 * config. Forces the signature-fingerprint getter `vf.e.c()` to return [OFFICIAL_SIG_HASH] (the
 * value the official build uses). [OFFICIAL_SIG_HASH] only changes if vpnify rotates its signing
 * key.
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
