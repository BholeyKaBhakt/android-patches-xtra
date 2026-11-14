package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SPEEDTEST
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val adFreePatch = patch(
    name = "AdFree Account",
) {
    compatibleWith(COMPATIBILITY_SPEEDTEST)

    execute {
        IsAdFreeAccountFingerprint.logMatch.method.returnEarly(true)
    }
}
