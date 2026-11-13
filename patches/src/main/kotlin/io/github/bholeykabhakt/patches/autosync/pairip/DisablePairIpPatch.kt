package io.github.bholeykabhakt.patches.autosync.pairip

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

// Nameless: runs only as a dependency of `purchaseAllItemsPatch` so users can't
// accidentally skip it. PairIP must be neutralised for the IAB bypass to hold.
val disablePairIpPatch = patch {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    execute {
        // PairIP enters through LicenseContentProvider.onCreate -> LicenseClient.initializeLicenseCheck().
        PairIpInitializeLicenseCheckFingerprint.logMatch.method.returnEarly()
    }
}
