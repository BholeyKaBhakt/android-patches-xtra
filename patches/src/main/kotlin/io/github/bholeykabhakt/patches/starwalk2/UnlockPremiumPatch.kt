package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary

/**
 * Star Walk 2 (Unity / IL2CPP). Four ARM64 rewrites in `lib/arm64-v8a/libil2cpp.so` unlock the
 * premium tier and make the Add-On Content store usable: force the license check and
 * `get_HasFullAccess`, surface the store entry via `ItemBought`, and flip an un-owned add-on's
 * action button from "Buy" to "Install" so its pack downloads for free. Offsets are per-version;
 * the original bytes at each site are asserted before writing, so a stale offset fails loudly
 * instead of corrupting the .so.
 */

internal const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

// ARM64 instruction encodings (little-endian)
private const val MOV_W8_1 = 0x52800028             // MOV W8, #1
private const val MOV_W8_4 = 0x52800088             // MOV W8, #4
private const val STRB_W8_X0_48 = 0x39012008        // STRB W8, [X0, #0x48]

// prologue words asserted at each site (guard against a stale offset table)
private const val STP_X30_X19_PRE = 0xA9BF4FFE.toInt()  // STP X30, X19, [SP, #-0x10]!
private const val LDR_X8_X0_20 = 0xF9401008.toInt()     // LDR X8, [X0, #0x20]
private const val MOV_X19_X0 = 0xAA0003F3.toInt()       // MOV X19, X0
private const val STR_X30_SP_M20 = 0xF81E0FFE.toInt()   // STR X30, [SP, #-0x20]!
private const val STP_X20_X19_SP10 = 0xA9014FF4.toInt() // STP X20, X19, [SP, #0x10]
private const val SUB_SP_SP_60 = 0xD10183FF.toInt()     // SUB SP, SP, #0x60
private const val STR_X30_SP_30 = 0xF9001BFE.toInt()    // STR X30, [SP, #0x30]

private fun invalidLicense(offset: Int) = BinaryPatch(
    "GooglePlay.InvalidLicense()", offset,
    expected = intArrayOf(STP_X30_X19_PRE, LDR_X8_X0_20, MOV_X19_X0),
    replacement = intArrayOf(MOV_W8_1, STRB_W8_X0_48, Arm64.RET),
)

private fun hasFullAccess(offset: Int) = BinaryPatch(
    "StorePanelManager.get_HasFullAccess()", offset,
    expected = intArrayOf(STR_X30_SP_M20, STP_X20_X19_SP10),
    replacement = intArrayOf(Arm64.MOV_W0_1, Arm64.RET),
)

private fun showInstallNotBuy(offset: Int) = BinaryPatch(
    "StoreEntry.set_State(None): action button Buy -> Download (\"Install\")", offset,
    expected = intArrayOf(MOV_W8_1),
    replacement = intArrayOf(MOV_W8_4),
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
    "2.20.3" to listOf(
        invalidLicense(0x3CE8274),
        hasFullAccess(0x3DA9A50),
        showInstallNotBuy(0x22F113C),
        itemBought(0x22FA26C),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    execute {
        val version = packageMetadata.versionName
        val patches = STAR_WALK_2_PREMIUM_PATCHES.forVersion("Star Walk 2", version, "Add offsets via Il2CppDumper.")
        patchNativeLibrary(LIBIL2CPP_PATH, "Star Walk 2", version, patches)
    }
}
