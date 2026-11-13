package io.github.bholeykabhakt.patches.autosync

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

// Base32(SHA1(the stock ttxapps signing cert)) — unchanged across versions.
private const val STOCK_SIGNATURE_TOKEN = "5BEWTYHAZIWM7QOCWWMDM2AZOASAU6GL"

// Forces the signer-hash computer to the stock token and nulls the remote tamper-blacklist getters,
// so a re-signed build is never flagged (no trial downgrade / folder-pair wipe). Nameless dependency.
val forceIntegrityStatePatch = patch {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    execute {
        SignerHashComputerFingerprint.logMatch.method.returnEarly(STOCK_SIGNATURE_TOKEN)
        BadApkSigGetterFingerprint.logMatch.method.returnEarly()
        BadUnlockCodeGetterFingerprint.logMatch.method.returnEarly()
    }
}
