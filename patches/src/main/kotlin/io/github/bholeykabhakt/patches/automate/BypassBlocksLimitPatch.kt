package io.github.bholeykabhakt.patches.automate

import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.utils.returnEarly

@Suppress("unused")
val bypassBlocksLimitPatch = bytecodePatch(
    name = "Bypass Blocks Limit",
) {
    compatibleWith("com.llamalab.automate")

    execute {

        isBlockLimitReachedFingerPrint.method.returnEarly("0x1")

    }
}