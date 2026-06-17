package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stellarium Mobile (Qt6 / C++). Unlocks the Plus tier via ARM64 rewrites in
 * `lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so`:
 *
 *  - the two `StelAPI` Plus getters (`getHasFeaturePLUS`, `hasValidLicense`) are rewritten to
 *    `MOV X0,X8 ; MOV W1,#1 ; B QVariant::QVariant(bool)` — constructs `QVariant(true)` into the
 *    caller's sret buffer (a bare `MOV W0,#1 ; RET` SIGSEGVs, since the caller reads the buffer
 *    via `QVariant::isNull()`); the branch displacement is computed at patch time, and
 *  - `getAppVariant()`'s flag branch is NOP-ed so it always reports "plus", removing the upsell.
 *
 * Offsets are per-version; entry bytes are asserted before writing.
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
    /** NOP `getAppVariant()`'s flag branch so the QML "Stellarium PLUS" upsell item is never inserted. */
    val wordPatches: List<WordPatch>,
)

private val PATCHES_BY_VERSION = mapOf(
    "1.16.2" to Targets(
        qvariantBoolCtorPltAddr = 0x8AB690,
        wordPatches = listOf(
            // getAppVariant: `cbz w9, <free>` → nop ⇒ always falls through to "plus".
            WordPatch("getAppVariant() force plus", 0x736F98, 0x34000089, NOP),
        ),
        sites = listOf(
            // getHasFeaturePLUS opens with a big stack frame (160 B + 12 callee saves)
            // because the stock body walks a QList<QString>.
            Site(
                label = "StelAPI::getHasFeaturePLUS()",
                offset = 0x72FB00,
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
                offset = 0x737B84,
                expectedFirstWords = intArrayOf(
                    0xB9401809.toInt(),  // LDR W9, [X0, #0x18]
                    0x7100053F,          // CMP W9, #0x1
                ),
            ),
        ),
    ),
    "1.16.3" to Targets(
        qvariantBoolCtorPltAddr = 0x8ACCE0,
        wordPatches = listOf(
            WordPatch("getAppVariant() force plus", 0x737BAC, 0x34000089, NOP),
        ),
        sites = listOf(
            Site(
                label = "StelAPI::getHasFeaturePLUS()",
                offset = 0x73069C,
                expectedFirstWords = intArrayOf(
                    0xD10283FF.toInt(),  // SUB SP, SP, #0xA0
                    0xA9047BFD.toInt(),  // STP X29, X30, [SP, #0x40]
                ),
            ),
            Site(
                label = "StelAPI::hasValidLicense()",
                offset = 0x738798,
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

    dependsOn(disableAnalyticsPatch)

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
