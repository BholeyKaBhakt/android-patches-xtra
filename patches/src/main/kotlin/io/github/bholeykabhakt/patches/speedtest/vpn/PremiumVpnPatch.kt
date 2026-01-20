package io.github.bholeykabhakt.patches.speedtest.vpn

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val premiumVpnPatch = bytecodePatch(
    name = "Premium VPN Patch",
) {
    compatibleWith("org.zwanoo.android.speedtest")

    execute {

        val smaliInstructions =
            """
                new-instance v0, Lcom/ookla/speedtest/vpn/CachedVpnAccount;
                const-string v2, "VPN_PAID"
                const/4 v3, 0x0
                move-object v1, v0
                invoke-direct {v1, v2, v2, v3}, Lcom/ookla/speedtest/vpn/CachedVpnAccount;-><init>(Ljava/lang/String;Ljava/lang/String;Lcom/ookla/speedtest/vpn/VpnAccountUsage;)V
                return-object v0
            """
        getCachedVpnAccountFingerprint.method.addInstructions(0, smaliInstructions.trimIndent())
    }
}