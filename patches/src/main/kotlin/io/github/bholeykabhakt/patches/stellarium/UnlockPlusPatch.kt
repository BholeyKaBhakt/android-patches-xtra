package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stellarium Mobile is a Qt6 / C++ app. Java is a thin Qt-for-Android shim, all
 * the Plus-tier gating runs as native ARM64 in `libstellarium-mobile-plus_arm64-v8a.so`.
 *
 * The Free and Plus SKUs ship the **same** binary; the runtime decides which
 * features to unlock by consulting two Q_PROPERTYs on the `StelAPI` singleton:
 *
 *  - `hasFeaturePLUS` — has the user paid for Plus? Stock body walks
 *    `Billing::purchasesList` matching against the two product IDs
 *    `stellarium_plus_subscription` / `stellarium_plus_one_time_purchase`.
 *
 *  - `hasValidLicense` — has Google Play LVL signed off on this install?
 *    Stock body reads a license-status field set asynchronously by the JNI
 *    callback `Java_…_Stellarium_cbLicenceStatusChanged`. Returns `false`
 *    when the device has no signed-in Google account (reason `0xFAB2`) or
 *    when the install was sideloaded / resigned — which makes the Plus
 *    page show "this copy isn't genuine".
 *
 * Both accessors return a `QVariant` (not a bare `bool` — important — see
 * below) via the ARM64 sret convention. We replace each function entry with
 * the same 3-instruction tail-call that constructs `QVariant(true)` directly
 * into the caller-provided sret buffer:
 *
 *   MOV X0, X8        ; x0 = sret output pointer (caller put it in x8)
 *   MOV W1, #1        ; w1 = bool value (true)
 *   B   QVariant::QVariant(bool)@plt   ; tail-call → ctor RETs back to caller
 *
 * **Why not the simpler `MOV W0,#1 ; RET`?** That was the first attempt and
 * it segfaults the app on launch. The caller (`StelAPI::setAssetHook()`,
 * which runs from the constructor) sets `x8 = &local_QVariant` as the sret
 * output, calls the getter, then immediately calls `QVariant::isNull()` on
 * the buffer. A bare `RET` leaves the buffer holding uninitialized stack
 * garbage → `isNull()` follows a bad d-pointer → SIGSEGV deep inside
 * `libQt6Core`. The tail-call into `QVariant::QVariant(bool)` constructs a
 * proper `QVariant(true)` into `[x8]` and returns — same shape as the existing
 * `StelAPI::hasValidLicense()` "return QVariant(true)" path at
 * `0x72E930..0x72E938`.
 *
 * Catalog/data note: most star, DSO and survey content is **not** Plus-gated —
 * Stellarium uses the standard HiPS protocol with a public DigitalOcean Spaces
 * CDN (`stellarium.sfo2.cdn.digitaloceanspaces.com`) and progressively fetches
 * deeper HEALPix tiles as the user zooms. Setting `hasFeaturePLUS=true` unlocks
 * UI features (telescope control, advanced overlays, all skycultures, extra
 * landscapes) and the deep-survey tile fetches those features trigger; the
 * baseline mag-7 stars + DSO catalog ships bundled in `assets/data/`.
 *
 * Truly-Plus extra (see [Targets.wordPatches]): the main menu inserts a
 * "Stellarium PLUS" upsell item via QML JS, gated on `App.appVariant === "free"`.
 * `StelAPI::getAppVariant()` returns "plus"/"free" by a byte flag; NOP-ing its
 * `cbz` makes it always return "plus", so the QML skips the insert and the item
 * (and its icon) is never created. `appVariant` has exactly one consumer (that
 * menu check; its only native caller is `qt_metacall`), so this does not touch
 * feature gating — that is the separate `hasFeaturePLUS` above.
 *
 * Adding a new version: pull the new `libstellarium-mobile-plus_arm64-v8a.so`,
 * locate the getter via `nm -D <so> | c++filt | grep getHasFeaturePLUS`,
 * locate the QVariant(bool) PLT via
 * `nm -D <so> | c++filt | grep 'QVariant::QVariant(bool)@plt'` (or
 * `xcrun llvm-objdump -d <so> | grep -B 1 _ZN8QVariantC1Eb@plt`), and append
 * an entry to [PATCHES_BY_VERSION]. The branch displacement is recomputed
 * at patch time.
 */

// ARM64 instruction encodings (little-endian)
private const val MOV_X0_X8 = 0xAA0803E0.toInt()      // MOV X0, X8
private const val MOV_W1_1 = 0x52800021                // MOV W1, #1
private const val B_OPCODE = 0x14000000                // B imm26 base
private const val NOP = 0xD503201F.toInt()             // NOP

private const val LIB_PATH = "lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so"

/** One patch site: function entry to rewrite into the QVariant(true) tail-call. */
private data class Site(
    val label: String,
    val offset: Int,
    /** First two instructions expected at [offset] — sanity check before patching. */
    val expectedFirstWords: IntArray,
)

/** Single-word replacement (e.g. NOP out a branch). */
private data class WordPatch(val label: String, val offset: Int, val expect: Int, val replace: Int)

private data class Targets(
    val qvariantBoolCtorPltAddr: Int,
    val sites: List<Site>,
    /**
     * Makes it *truly* Plus: removes the "Stellarium PLUS" upsell menu item.
     * The main menu QML inserts that item only when `App.appVariant === "free"`.
     * `StelAPI::getAppVariant()` returns "plus"/"free" by a byte flag; NOP-ing the
     * flag branch makes it always return "plus", so the item (and its icon) is
     * never created — cleaner than blanking the label, which leaves an empty row.
     */
    val wordPatches: List<WordPatch>,
)

private val PATCHES_BY_VERSION = mapOf(
    "1.16.0" to Targets(
        qvariantBoolCtorPltAddr = 0x8A0DA0,
        wordPatches = listOf(
            // getAppVariant: `cbz w9, <free>` → nop ⇒ always falls through to "plus".
            WordPatch("getAppVariant() force plus", 0x72DD00, 0x34000089, NOP),
        ),
        sites = listOf(
            // getHasFeaturePLUS opens with a big stack frame (160 B + 12 callee saves)
            // because the stock body walks a QList<QString>.
            Site(
                label = "StelAPI::getHasFeaturePLUS()",
                offset = 0x725520,
                expectedFirstWords = intArrayOf(
                    0xD10283FF.toInt(),  // SUB SP, SP, #0xA0
                    0xA9047BFD.toInt(),  // STP X29, X30, [SP, #0x40]
                ),
            ),
            // hasValidLicense is small — reads status from [this+0x18], branches
            // on its value to one of two pre-built "return QVariant(true/false)"
            // tail-calls. Patching the entry collapses the whole decision tree.
            Site(
                label = "StelAPI::hasValidLicense()",
                offset = 0x72E8EC,
                expectedFirstWords = intArrayOf(
                    0xB9401809.toInt(),  // LDR W9, [X0, #0x18]
                    0x7100053F,          // CMP W9, #0x1
                ),
            ),
        ),
    ),
)

@Suppress("unused")
val unlockPlusPatch = rawResourcePatch(
    name = "Unlock Plus",
) {
    compatibleWith(COMPATIBILITY_STELLARIUM)

    execute {
        val version = packageMetadata.versionName
        val targets = PATCHES_BY_VERSION[version] ?: throw PatchException(
            "Stellarium version $version is not supported. " +
                    "Supported: ${PATCHES_BY_VERSION.keys.joinToString()}. " +
                    "Add offsets via `nm -D <so> | c++filt` (see KDoc).",
        )

        val so = get(LIB_PATH)
        if (!so.exists()) throw PatchException("$LIB_PATH not found in APK")

        val bytes = so.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        targets.sites.forEach { site ->
            val branchPc = site.offset + 8 // B is the third instruction
            val branchEnc = encodeB(branchPc, targets.qvariantBoolCtorPltAddr)
            val replacement = intArrayOf(MOV_X0_X8, MOV_W1_1, branchEnc)

            // Idempotency: skip if first two replacement words are already in place.
            if (buffer.getInt(site.offset) == replacement[0] &&
                buffer.getInt(site.offset + 4) == replacement[1]
            ) return@forEach

            if (buffer.getInt(site.offset) != site.expectedFirstWords[0] ||
                buffer.getInt(site.offset + 4) != site.expectedFirstWords[1]
            ) {
                throw PatchException(
                    "${site.label} @ 0x${site.offset.toString(16).uppercase()} — " +
                            "expected entry bytes don't match; the offset table for v$version is stale",
                )
            }

            replacement.forEachIndexed { i, insn ->
                buffer.putInt(site.offset + i * 4, insn)
            }
        }

        targets.wordPatches.forEach { wp ->
            val cur = buffer.getInt(wp.offset)
            if (cur == wp.replace) return@forEach // idempotent
            if (cur != wp.expect) {
                throw PatchException(
                    "${wp.label} @ 0x${wp.offset.toString(16).uppercase()} — expected " +
                            "0x${wp.expect.toUInt().toString(16)} but found " +
                            "0x${cur.toUInt().toString(16)}; offset table stale for v$version",
                )
            }
            buffer.putInt(wp.offset, wp.replace)
        }

        so.writeBytes(bytes)
    }
}

/** ARM64 B (unconditional branch) imm26 encoding. Throws if out of range. */
private fun encodeB(pc: Int, target: Int): Int {
    val byteOffset = target - pc
    if (byteOffset % 4 != 0) {
        throw PatchException("B target not 4-byte aligned (pc=0x$pc target=0x$target)")
    }
    val wordOffset = byteOffset shr 2
    // imm26 is a signed 26-bit field
    val min = -(1 shl 25)
    val max = (1 shl 25) - 1
    if (wordOffset < min || wordOffset > max) {
        throw PatchException("B target out of ±128MB range (pc=0x$pc target=0x$target)")
    }
    return B_OPCODE or (wordOffset and 0x03FFFFFF)
}
