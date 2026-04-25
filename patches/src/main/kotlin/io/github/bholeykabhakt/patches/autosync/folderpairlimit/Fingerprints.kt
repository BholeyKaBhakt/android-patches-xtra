package io.github.bholeykabhakt.patches.autosync.folderpairlimit

import app.morphe.patcher.Fingerprint

internal object SyncSettingsBFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/sync/SyncSettings",
    name = "b",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("PREF_UPGRADED_AT"),
)

internal object SyncSettingsGetLastUpdatedAtFingerprint : Fingerprint(
    definingClass = "Lcom/ttxapps/autosync/sync/SyncSettings;",
    returnType = "J",
    parameters = emptyList(),
    strings = listOf("PREF_LAST_UPDATED_AT"),
)
