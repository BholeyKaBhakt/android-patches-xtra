package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val adFreePatch = bytecodePatch(
    name = "AdFree Account Patch",
) {
    compatibleWith("org.zwanoo.android.speedtest")

    execute {
        // always return true for isAdFreeAccount()
        isAdFreeAccountFingerprint.method.returnEarly("0x1")

        // skip checkPurchases()
        checkPurchasesFingerprint.method.returnEarly()
    }
}