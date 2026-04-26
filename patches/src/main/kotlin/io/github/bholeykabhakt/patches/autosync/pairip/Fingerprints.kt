package io.github.bholeykabhakt.patches.autosync.pairip

import app.morphe.patcher.Fingerprint

/**
 * PairIP is Google's licensing/integrity SDK shipped as `com.pairip.licensecheck.*`.
 * Both the package and the public method name `initializeLicenseCheck()V` are part of
 * the SDK's third-party API contract, so they are stable across host-app obfuscation.
 */
internal object PairIpInitializeLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "initializeLicenseCheck",
    returnType = "V",
    parameters = emptyList(),
)
