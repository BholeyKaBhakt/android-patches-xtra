package io.github.bholeykabhakt.patches.autosync.pairip

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val disablePairIpPatch = bytecodePatch(
    name = "Disable PairIP License Check",
) {
    compatibleWith(Compatibility(packageName = "com.ttxapps.autosync"))

    execute {
        // PairIP enters through LicenseContentProvider.onCreate -> LicenseClient.initializeLicenseCheck().
        PairIpInitializeLicenseCheckFingerprint.logMatch.method.returnEarly()
    }
}
