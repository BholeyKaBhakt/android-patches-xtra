package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_UFOVPN
import io.github.bholeykabhakt.patches.utils.returnEarly

/** Neuters PairIP's licensecheck boot gate so a re-signed build runs (no native VM in this app). */
@Suppress("unused")
val disablePairipPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_UFOVPN)

    execute {
        val licenseClient = mutableClassDefBy("Lcom/pairip/licensecheck/LicenseClient;")
        licenseClient.methods.first { it.name == "checkLicense" }.returnEarly()
        licenseClient.methods.firstOrNull { it.name == "initializeLicenseCheck" }?.returnEarly()
    }
}
