package io.github.bholeykabhakt.patches.autosync.temperdetection

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val disableTemperDetectionPatch = bytecodePatch(
    name = "Disable Temper Detection",
) {
    compatibleWith("com.ttxapps.autosync")

    execute {

        // VarH => g$a.f() = false (just like old z.g = false but on getter)
        temperDetectionVarHGetterFingerprint.method.returnEarly("0x0")

        // VarZ => i.z() = false (just like old SyncState.z())
        temperDetectionVarZGetterFingerprint.method.returnEarly("0x0")
    }
}