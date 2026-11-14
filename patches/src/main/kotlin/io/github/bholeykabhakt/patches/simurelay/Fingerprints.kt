package io.github.bholeykabhakt.patches.simurelay

import app.morphe.patcher.Fingerprint

/** Handler of the `com.mordiastudio/antimod` method channel: one case per query name. */
internal object AntiModHandlerFingerprint : Fingerprint(
    strings = listOf("isSameAppSignature", "isSamePackage", "isEmulator", "isDebuggable"),
)

/** `configureFlutterEngine`, which runs before the Dart entrypoint reads shared preferences. */
internal object ConfigureFlutterEngineFingerprint : Fingerprint(
    definingClass = "Lcom/mordiastudio/simurelayfree/MainActivity;",
    returnType = "V",
    strings = listOf("flutterEngine"),
)
