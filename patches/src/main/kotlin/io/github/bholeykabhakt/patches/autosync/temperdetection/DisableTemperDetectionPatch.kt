package io.github.bholeykabhakt.patches.autosync.temperdetection

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val disableTemperDetectionPatch = bytecodePatch(
    name = "Disable Temper Detection",
) {
    compatibleWith(Compatibility(packageName = "com.ttxapps.autosync"))

    execute {
        // VarH => g$a.f()Z = false (just like old z.g = false but on getter)
        TemperDetectionVarHGetterFingerprint.logMatch.method.returnEarly("0x0")

        // VarZ => i.z()Z = false (just like old SyncState.z())
        TemperDetectionVarZGetterFingerprint.logMatch.method.returnEarly("0x0")
    }
}