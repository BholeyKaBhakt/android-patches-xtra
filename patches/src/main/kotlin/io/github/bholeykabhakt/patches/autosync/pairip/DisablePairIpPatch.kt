package io.github.bholeykabhakt.patches.autosync.pairip

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val disablePairIpPatch = bytecodePatch(
    name = "Disable PairIP License Check",
) {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    execute {
        // PairIP enters through LicenseContentProvider.onCreate -> LicenseClient.initializeLicenseCheck().
        PairIpInitializeLicenseCheckFingerprint.logMatch.method.returnEarly()
    }
}
