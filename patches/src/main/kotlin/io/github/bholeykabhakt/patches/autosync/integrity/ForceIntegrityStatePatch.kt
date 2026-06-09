package io.github.bholeykabhakt.patches.autosync.integrity

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val STOCK_SIGNATURE_TOKEN = "5BEWTYHAZIWM7QOCWWMDM2AZOASAU6GL"

/**
 * Two surgical patches that together neutralise every tamper / trial / lock path:
 *
 *   1. Force the signer-hash computer to return the stock token.
 *      → local pattern check in `SyncService.j` always passes
 *      → `jl7.h` never set true
 *      → `rl7.b()` (trial downgrade — PIN/Pro caps + folder list trimmed) never runs
 *      → 18 h periodic re-check in `hk7.a` always passes
 *      → sync-flow short-circuit in `rk7.k` never fires
 *      → StatusFragment warning dialog never shows
 *
 *   2. Null both getters on the remote-config DTO (`badApkSig`, `badUnlockCode`).
 *      → server blacklist comparison in `SyncService.j` short-circuits on the
 *        `if-eqz` immediately before `ug7.F0(...)` — `vl7.a` never set true
 *      → 18 h folder-pair wipe in `rk7.k` never fires
 *      → `PREF_UNLOCK_CODE` revocation in `SyncService.j` never matches
 *      → MainActivity warning dialog never shows
 */
@Suppress("unused")
val forceIntegrityStatePatch = bytecodePatch(
    name = "Force Stable Integrity State (Critical)",
) {
    compatibleWith(Compatibility(packageName = "com.ttxapps.autosync", name = "Autosync"))

    execute {
        SignerDigestComputerFingerprint.logMatch.method.returnEarly(STOCK_SIGNATURE_TOKEN)
        BadApkSigGetterFingerprint.logMatch.method.returnEarly()
        BadUnlockCodeGetterFingerprint.logMatch.method.returnEarly()
    }
}
