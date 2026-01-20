package io.github.bholeykabhakt.patches.speedtest.vpn

import app.revanced.patcher.fingerprint

// Lcom/ookla/mobile4/screens/main/vpn/VpnPrefsImpl;->getCachedVpnAccount()Lcom/ookla/speedtest/vpn/CachedVpnAccount;
internal val getCachedVpnAccountFingerprint = fingerprint {
    returns("Lcom/ookla/speedtest/vpn/CachedVpnAccount;")
    parameters()
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/mobile4/screens/main/vpn/VpnPrefsImpl;") && method.name == "getCachedVpnAccount"
    }
}