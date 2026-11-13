package io.github.bholeykabhakt.patches.autosync

import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOSYNC
import io.github.bholeykabhakt.patches.utils.PAIRIP_LICENSE_CHECK
import io.github.bholeykabhakt.patches.utils.pairIpVmPatch

val dePairIpVmPatch = pairIpVmPatch(COMPATIBILITY_AUTOSYNC, "autosync", PAIRIP_LICENSE_CHECK to "checkLicense")
