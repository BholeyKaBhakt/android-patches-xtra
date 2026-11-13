package io.github.bholeykabhakt.patches.zangi

import app.morphe.patcher.Fingerprint

// Zangi keeps its own class and member names (R8 only renames libraries), so these anchor on them.

internal const val MAIN_APPLICATION = "Lcom/beint/project/MainApplication;"
internal const val MAIN_ACTIVITY = "Lcom/beint/project/MainActivity;"

internal object AttachBaseContextFingerprint : Fingerprint(
    definingClass = MAIN_APPLICATION,
    name = "attachBaseContext",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

internal object ApplicationOnCreateFingerprint : Fingerprint(
    definingClass = MAIN_APPLICATION,
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
)

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

internal object MainActivityOnStopFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "onStop",
    returnType = "V",
    parameters = emptyList(),
)

/** Settings › "Transfer to new device" row handler. */
internal object OpenTransferDialogFingerprint : Fingerprint(
    definingClass = "Lcom/beint/project/screens/settings/more/settings/ScreenTabMore;",
    name = "openTransferDialogWithAuthCheck",
    returnType = "V",
    parameters = emptyList(),
)

/** `PremiumManager.isPremium()`: reads the server-pushed `USER_HAS_SUBSCRIPTION` flag. */
internal object IsPremiumFingerprint : Fingerprint(
    definingClass = "Lcom/beint/project/core/managers/PremiumManager;",
    name = "isPremium",
    returnType = "Z",
    parameters = emptyList(),
)

/** Signaling registration handler: applies the server `premium` flag and turns VPN tunneling off when it's "0". */
internal object OnRtmpEventFingerprint : Fingerprint(
    definingClass = "Lcom/beint/project/core/services/impl/SignalingService;",
    name = "OnRtmpEvent",
    returnType = "I",
    parameters = listOf("Lcom/beint/project/core/RtmpEvent;"),
)
