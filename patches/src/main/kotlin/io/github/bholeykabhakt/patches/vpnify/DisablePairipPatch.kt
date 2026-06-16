package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.gutPairIpVm
import io.github.bholeykabhakt.patches.utils.restorePairIpHolders

/**
 * Statically **de-PairIPs** stock vpnify so a re-signed build runs — a complete static replacement
 * for PairIP's dynamic native VM:
 *
 *  1. **Gut the VM** + integrity check via [gutPairIpVm].
 *  2. **Restore the string + reflect-`Method` holder tables** the VM would have populated, from the
 *     bundled `vpnify/depairip_strings.tsv` + `vpnify/depairip_methods.tsv` harvest, via
 *     [restorePairIpHolders].
 *  3. **Restore the de-virtualized helper classes** the reflect table dispatches to, merged from
 *     `extensions/vpnifydepairip.mpe` via [extendWith].
 *
 * The `.tsv`/`.mpe` resources are per-build (obfuscated names + harvested data); regenerate them on
 * a version bump. The gut/restore logic itself is PairIP-generic.
 */
@Suppress("unused")
val disablePairipPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_VPNIFY)

    // The restored helper classes (new types) the reflect table dispatches to.
    extendWith("extensions/vpnifydepairip.mpe")

    execute {
        gutPairIpVm()
        restorePairIpHolders("vpnify")
    }
}
