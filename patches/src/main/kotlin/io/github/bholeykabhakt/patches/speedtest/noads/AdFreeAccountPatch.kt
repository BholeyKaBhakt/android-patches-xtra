package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val adFreePatch = bytecodePatch(
    name = "AdFree Account Patch",
) {
    compatibleWith(Compatibility(packageName = "org.zwanoo.android.speedtest"))

    execute {
        // always return true for isAdFreeAccount()Z
        IsAdFreeAccountFingerprint.logMatch.method.returnEarly(true)

        // skip checkPurchases(Z)V
        CheckPurchasesFingerprint.logMatch.method.returnEarly()
    }
}