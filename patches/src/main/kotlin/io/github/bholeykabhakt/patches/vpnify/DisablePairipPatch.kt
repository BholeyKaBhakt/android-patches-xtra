package io.github.bholeykabhakt.patches.vpnify

import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.pairIpVmPatch

val disablePairipPatch = pairIpVmPatch(COMPATIBILITY_VPNIFY, "vpnify")
