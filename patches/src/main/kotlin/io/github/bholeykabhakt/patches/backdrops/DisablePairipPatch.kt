package io.github.bholeykabhakt.patches.backdrops

import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_BACKDROPS
import io.github.bholeykabhakt.patches.utils.PAIRIP_LICENSE_CHECK
import io.github.bholeykabhakt.patches.utils.pairIpVmPatch

val disablePairipPatch = pairIpVmPatch(COMPATIBILITY_BACKDROPS, "backdrops", PAIRIP_LICENSE_CHECK to "checkLicense")
