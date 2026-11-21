package io.github.bholeykabhakt.patches.cosmicwatch

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_COSMIC_WATCH
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * `libil2cpp.so`: `AccessManager.EffectiveTier()` → `FeatureTier.Pro` (100). Every gate (`IsPro`,
 * `CanUseTools`, `HasCoreAccess`, `HasPaidTier`, the tools, blockers and settings) reads it.
 */

// ARM64 instruction encodings (little-endian)
internal const val PACIASP = 0xD503233F.toInt()
internal const val STR_X30_SP_M20 = 0xF81E0FFE.toInt()  // STR X30, [SP, #-0x20]!
private const val MOV_W0_100 = 0x52800C80               // MOV W0, #100

private fun effectiveTierPro(offset: Int) = BinaryPatch(
    "AccessManager.EffectiveTier()", offset,
    expected = intArrayOf(PACIASP, STR_X30_SP_M20),
    replacement = intArrayOf(MOV_W0_100, Arm64.RET),
)

internal val COSMIC_WATCH_PRO_PATCHES = mapOf(
    "3.0.1" to listOf(
        effectiveTierPro(0x162F568),
    ),
)

@Suppress("unused")
val unlockProPatch = patch(
    name = "Unlock Pro",
) {
    compatibleWith(COMPATIBILITY_COSMIC_WATCH)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Cosmic Watch", COSMIC_WATCH_PRO_PATCHES)
    }
}
