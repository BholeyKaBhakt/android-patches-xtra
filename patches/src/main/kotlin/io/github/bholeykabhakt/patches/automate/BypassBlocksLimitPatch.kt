package io.github.bholeykabhakt.patches.automate

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val bypassBlocksLimitPatch = bytecodePatch(
    name = "Bypass Blocks Limit",
) {
    compatibleWith(Compatibility(packageName = "com.llamalab.automate"))

    execute {
        IsBlockLimitReachedFingerprint.method.returnEarly("0x1")
    }
}