package io.github.bholeykabhakt.patches.autosync.integrity

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val STOCK_SIGNATURE_TOKEN = "5BEWTYHAZIWM7QOCWWMDM2AZOASAU6GL"

/**
 * Neutralises autosync's tamper / trial / lock paths: forces the signer-hash computer to return
 * the stock token, and nulls the two remote-config blacklist getters (`badApkSig`,
 * `badUnlockCode`). Nameless — runs only as a dependency of `purchaseAllItemsPatch` (the IAB
 * bypass is meaningless without these short-circuits).
 */
val forceIntegrityStatePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    execute {
        SignerDigestComputerFingerprint.logMatch.method.returnEarly(STOCK_SIGNATURE_TOKEN)
        BadApkSigGetterFingerprint.logMatch.method.returnEarly()
        BadUnlockCodeGetterFingerprint.logMatch.method.returnEarly()
    }
}
