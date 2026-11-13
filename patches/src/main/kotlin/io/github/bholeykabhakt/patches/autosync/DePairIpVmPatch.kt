package io.github.bholeykabhakt.patches.autosync

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.gutPairIpVm
import io.github.bholeykabhakt.patches.utils.restorePairIpHolders

// Static de-PairIP for the native VM: gut the VM + license check, then restore the
// VM-populated string/Method holder tables and the de-virtualized $c body classes from the bundled
// per-build resources (depairip_strings.tsv / depairip_methods.tsv / autosyncdepairip.mpe). The
// generic gut/restore logic lives in the shared utils/PairIp.kt helpers.
val dePairIpVmPatch = patch {
    compatibleWith(COMPATIBILITY_AUTOSYNC)

    extendWith("extensions/autosyncdepairip.mpe")

    execute {
        // Extra gut target beyond the generic set: LicenseClient.checkLicense reaches LicenseActivity
        // on a re-signed/sideloaded install and force-finishes the app (the final boot blocker).
        gutPairIpVm("Lcom/pairip/licensecheck/LicenseClient;" to "checkLicense")
        restorePairIpHolders("autosync")
    }
}
