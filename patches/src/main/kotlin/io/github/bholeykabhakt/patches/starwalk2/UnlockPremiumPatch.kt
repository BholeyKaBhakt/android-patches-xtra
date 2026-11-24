package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import io.github.bholeykabhakt.patches.shared.LIBIL2CPP_PATH
import io.github.bholeykabhakt.patches.shared.MOV_W8_1
import io.github.bholeykabhakt.patches.shared.STP_X20_X19_SP10
import io.github.bholeykabhakt.patches.shared.invalidLicense
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.patchNativeLibraryForVersion

/**
 * `libil2cpp.so` rewrites:
 *   - `GooglePlay.InvalidLicense()` → set `updateStopped` and return: no paywall / self-exit
 *   - `StorePanelManager.get_HasFullAccess()` → true (premium tier)
 *   - `StoreProvider.ItemBought()` → true (surfaces the Add-On Content menu)
 *   - `StoreEntry.set_State` None branch: Buy → Download, so tiles show "Install"; the app's own
 *     downloader then fetches the pack (content on disk == installed)
 */

// ARM64 instruction encodings (little-endian)
private const val MOV_W8_4 = 0x52800088                 // MOV W8, #4
private const val SUB_SP_SP_60 = 0xD10183FF.toInt()     // SUB SP, SP, #0x60
private const val STR_X30_SP_30 = 0xF9001BFE.toInt()    // STR X30, [SP, #0x30]

private fun hasFullAccess(offset: Int) = BinaryPatch(
    "StorePanelManager.get_HasFullAccess()", offset,
    expected = intArrayOf(Arm64.STR_X30_SP_M20, STP_X20_X19_SP10),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

// `MOV W8,#1` alone matches almost anywhere: the branches on both sides pin the site.
private const val B_FWD_0x20 = 0x14000008  // B .+0x20
private const val B_FWD_0x1C = 0x14000007  // B .+0x1C

/** [offset] is the `MOV W8,#1` itself. */
private fun showInstallNotBuy(offset: Int) = BinaryPatch(
    "StoreEntry.set_State(None): action button Buy -> Download (\"Install\")", offset - 4,
    expected = intArrayOf(B_FWD_0x20, MOV_W8_1, B_FWD_0x1C),
    replacement = intArrayOf(B_FWD_0x20, MOV_W8_4, B_FWD_0x1C),
)

private fun itemBought(offset: Int) = BinaryPatch(
    "StoreProvider.ItemBought(string)", offset,
    expected = intArrayOf(SUB_SP_SP_60, STR_X30_SP_30),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

internal val STAR_WALK_2_PREMIUM_PATCHES = mapOf(
    "2.22.0" to listOf(
        invalidLicense(0x4022704),
        hasFullAccess(0x4102EF0),
        showInstallNotBuy(0x24A9B30),
        itemBought(0x24B2D04),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    execute {
        patchNativeLibraryForVersion(LIBIL2CPP_PATH, "Star Walk 2", STAR_WALK_2_PREMIUM_PATCHES)
    }
}
