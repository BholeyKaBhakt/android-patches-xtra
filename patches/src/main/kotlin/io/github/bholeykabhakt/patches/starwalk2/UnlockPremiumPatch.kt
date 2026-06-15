package io.github.bholeykabhakt.patches.starwalk2

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STAR_WALK_2
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Star Walk 2 by Vito Technology — Unity / IL2CPP (same engine as Sky Tonight). Every store /
 * entitlement decision runs as native ARM64 in `lib/arm64-v8a/libil2cpp.so`; this single
 * [rawResourcePatch] overwrites four function heads to fully unlock the app.
 *
 * Four sites (offsets = the `dump.cs` `Offset:` column; bytes asserted so a wrong-version match
 * fails loudly instead of corrupting the binary):
 *
 *   1. `GooglePlay.InvalidLicense()` → `MOV W8,#1 ; STRB W8,[X0,#0x48] ; RET`
 *      Byte-identical to Sky Tonight (same Vito license runner). Returns "license handled"
 *      without firing the Play-Store paywall intent — required for a re-signed APK to run.
 *   2. `StorePanelManager.get_HasFullAccess()` → `MOV W0,#1 ; RET`
 *      OR-combines IsLifetimeBought / IsAnySubscribed / IsReviewerStore; every premium gate
 *      reads it. Unlocks Sky Live, Visible Tonight, Astronomy Calendar, ad removal, and the
 *      "Lifetime Premium Access" state.
 *   3. `StoreEntry.set_State`, the State==None branch sets the action button to Buy(1); we
 *      rewrite that one immediate to Download(4) so an un-owned add-on tile shows **"Install"**
 *      instead of "Buy". Tapping it runs PerformAction's native download handler, which fetches
 *      just that pack for free from the open CloudFront CDN — nothing auto-downloads. On
 *      completion the app calls `set_State(Installed)` (untouched branch), which shows
 *      **"Installed"** and persists it; see the persistence note below.
 *   4. `StoreProvider.ItemBought(string)` → `MOV W0,#1 ; RET`
 *      Surfaces the **"Add-On Content"** entry in the main menu (without it the menu shows the
 *      "Premium Access" section instead and the store is unreachable).
 *
 * The add-on **content packs** (Deep Sky Objects, Extended Solar System, Satellites, Planets
 * Upgrade) are NOT bundled — the app downloads them at runtime from
 * `d1j18p2reqyyr1.cloudfront.net/slw_storage/{inappID}/Android/{version}/…` (open, no auth) and
 * extracts to `files/Storage/{inappID}/`. "Installed" is derived from on-disk content, so a
 * downloaded pack stays unlocked.
 *
 * Persistence note: the Installed tile state is saved via Unity `PlayerPrefs`, which flushes to
 * disk on app pause/background (normal use). A hard `am force-stop` immediately after a download
 * skips that flush — the *content* stays on disk, but the tile may read "Install" until tapped
 * once (a no-op re-validate; it does not re-download).
 *
 * Deliberately NOT patched: `ItemInstalled`, `StoreEntry.PrevInstalled`, `get_ContentDownloaded`
 * — those reflect real on-disk state; forcing them true makes the app think content is already
 * present and skip the CDN fetch.
 *
 * Adding a new version: decompile, run Il2CppDumper (Vito apps need metadata-v39 →
 * `roytu/Il2CppDumper` `v39` branch, `dotnet publish -f net8.0`), grep `dump.cs` for the four
 * declarations, take the `Offset:` column, append to [PATCHES_BY_VERSION]. (For site 3, the
 * offset is the `MOV W8,#1` immediate in `set_State`'s State==None branch, just before the
 * `STR Wn,[Xn,#0x10c]` that stores the action-button state.)
 */

// ARM64 instruction encodings (little-endian)
private const val MOV_W0_1 = 0x52800020             // MOV W0, #1
private const val MOV_W8_1 = 0x52800028             // MOV W8, #1
private const val MOV_W8_4 = 0x52800088             // MOV W8, #4
private const val STRB_W8_X0_48 = 0x39012008        // STRB W8, [X0, #0x48]
private const val RET = 0xD65F03C0.toInt()          // RET

// prologue words asserted at each site (guard against a stale offset table)
private const val STP_X30_X19_PRE = 0xA9BF4FFE.toInt()  // STP X30, X19, [SP, #-0x10]!
private const val LDR_X8_X0_20 = 0xF9401008.toInt()     // LDR X8, [X0, #0x20]
private const val MOV_X19_X0 = 0xAA0003F3.toInt()       // MOV X19, X0
private const val STR_X30_SP_M20 = 0xF81E0FFE.toInt()   // STR X30, [SP, #-0x20]!
private const val STP_X20_X19_SP10 = 0xA9014FF4.toInt() // STP X20, X19, [SP, #0x10]
private const val SUB_SP_SP_60 = 0xD10183FF.toInt()     // SUB SP, SP, #0x60
private const val STR_X30_SP_30 = 0xF9001BFE.toInt()    // STR X30, [SP, #0x30]

private const val LIBIL2CPP_PATH = "lib/arm64-v8a/libil2cpp.so"

private data class BinaryPatch(
    val label: String,
    val offset: Int,
    val expected: IntArray,
    val replacement: IntArray,
)

private fun invalidLicense(offset: Int) = BinaryPatch(
    "GooglePlay.InvalidLicense()", offset,
    expected = intArrayOf(STP_X30_X19_PRE, LDR_X8_X0_20, MOV_X19_X0),
    replacement = intArrayOf(MOV_W8_1, STRB_W8_X0_48, RET),
)

private fun hasFullAccess(offset: Int) = BinaryPatch(
    "StorePanelManager.get_HasFullAccess()", offset,
    expected = intArrayOf(STR_X30_SP_M20, STP_X20_X19_SP10),
    replacement = intArrayOf(MOV_W0_1, RET),
)

private fun showInstallNotBuy(offset: Int) = BinaryPatch(
    "StoreEntry.set_State(None): action button Buy -> Download (\"Install\")", offset,
    expected = intArrayOf(MOV_W8_1),
    replacement = intArrayOf(MOV_W8_4),
)

private fun itemBought(offset: Int) = BinaryPatch(
    "StoreProvider.ItemBought(string)", offset,
    expected = intArrayOf(SUB_SP_SP_60, STR_X30_SP_30),
    replacement = intArrayOf(MOV_W0_1, RET),
)

private val PATCHES_BY_VERSION = mapOf(
    "2.20.3" to listOf(
        invalidLicense(0x3CE8274),
        hasFullAccess(0x3DA9A50),
        showInstallNotBuy(0x22F113C),
        itemBought(0x22FA26C),
    ),
)

@Suppress("unused")
val unlockPremiumPatch = rawResourcePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_STAR_WALK_2)

    execute {
        val version = packageMetadata.versionName
        val patches = PATCHES_BY_VERSION[version] ?: throw PatchException(
            "Star Walk 2 version $version is not supported. " +
                    "Supported: ${PATCHES_BY_VERSION.keys.joinToString()}. Add offsets via Il2CppDumper.",
        )

        val so = get(LIBIL2CPP_PATH)
        if (!so.exists()) throw PatchException("$LIBIL2CPP_PATH not found in APK")

        val bytes = so.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        patches.forEach { p ->
            // Idempotency: skip if the replacement is already in place.
            if (matches(buffer, p.offset, p.replacement)) return@forEach
            if (!matches(buffer, p.offset, p.expected)) {
                throw PatchException(
                    "${p.label} @ 0x${p.offset.toString(16).uppercase()} — " +
                            "expected bytes don't match; the v$version offset table is stale",
                )
            }
            p.replacement.forEachIndexed { i, insn -> buffer.putInt(p.offset + i * 4, insn) }
        }
        so.writeBytes(bytes)
    }
}

private fun matches(buffer: ByteBuffer, offset: Int, words: IntArray): Boolean =
    words.withIndex().all { (i, w) -> buffer.getInt(offset + i * 4) == w }
