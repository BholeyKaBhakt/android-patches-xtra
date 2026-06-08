package io.github.bholeykabhakt.patches.circuitsimulator

import app.morphe.patcher.Fingerprint

/**
 * IAP-status enum, anchored by three of its value-name strings emitted by
 * `<clinit>`. "OK" is the unlocked state; "MISSING_*" are per-feature lock
 * reasons. The class descriptor is obfuscated and shifts per release; the
 * value names are compile-time-stable.
 */
internal object IapStatusEnumFingerprint : Fingerprint(
    strings = listOf("OK", "MISSING_SCOPE", "MISSING_SENSORS"),
)
