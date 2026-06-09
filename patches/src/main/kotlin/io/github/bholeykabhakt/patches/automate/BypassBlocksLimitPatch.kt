package io.github.bholeykabhakt.patches.automate

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_AUTOMATE
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val bypassBlocksLimitPatch = bytecodePatch(
    name = "Bypass Blocks Limit",
) {
    compatibleWith(COMPATIBILITY_AUTOMATE)

    execute {
        IsBlockLimitReachedFingerprint.logMatch.method.returnEarly(true)
    }
}
