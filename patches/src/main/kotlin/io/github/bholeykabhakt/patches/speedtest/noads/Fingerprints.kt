package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.fingerprint

// Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;->isAdFreeAccount()Z
internal val isAdFreeAccountFingerprint = fingerprint {
    returns("Z")
    parameters()
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;") && method.name == "isAdFreeAccount"
    }
}

// Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;->checkPurchases()V
internal val checkPurchasesFingerprint = fingerprint {
    returns("V")
    parameters("Z")
    custom { method, classDef ->
        classDef.equals("Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;") && method.name == "checkPurchases"
    }
}

