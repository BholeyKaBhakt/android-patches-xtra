package io.github.bholeykabhakt.patches.speedtest.noads

import app.morphe.patcher.Fingerprint

// Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;->isAdFreeAccount()Z
internal object IsAdFreeAccountFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;",
    name = "isAdFreeAccount",
    returnType = "Z",
    parameters = emptyList(),
)

// Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;->checkPurchases(Z)V
internal object CheckPurchasesFingerprint : Fingerprint(
    definingClass = "Lcom/ookla/speedtest/purchase/google/BillingClientPurchaseManager;",
    name = "checkPurchases",
    returnType = "V",
    parameters = listOf("Z"),
)

