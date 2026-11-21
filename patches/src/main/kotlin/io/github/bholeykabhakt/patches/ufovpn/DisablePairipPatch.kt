package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_UFOVPN
import io.github.bholeykabhakt.patches.utils.PAIRIP_LICENSE_CHECK
import io.github.bholeykabhakt.patches.utils.method
import io.github.bholeykabhakt.patches.utils.returnEarly

/** Neuters PairIP's licensecheck boot gate so a re-signed build runs (no native VM in this app). */
@Suppress("unused")
val disablePairipPatch = patch {
    compatibleWith(COMPATIBILITY_UFOVPN)

    execute {
        val licenseClient = mutableClassDefBy(PAIRIP_LICENSE_CHECK)
        licenseClient.method("checkLicense").returnEarly()
        licenseClient.methods.firstOrNull { it.name == "initializeLicenseCheck" }?.returnEarly()
    }
}
