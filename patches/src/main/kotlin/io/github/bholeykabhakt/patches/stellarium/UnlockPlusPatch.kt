package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM
import io.github.bholeykabhakt.patches.utils.Arm64
import io.github.bholeykabhakt.patches.utils.BinaryPatch
import io.github.bholeykabhakt.patches.utils.forVersion
import io.github.bholeykabhakt.patches.utils.patchNativeLibrary

/**
 * Stellarium Mobile (Qt6 / C++). Unlocks the Plus tier via ARM64 rewrites in
 * `lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so`:
 *
 *  - the two `StelAPI` Plus getters (`getHasFeaturePLUS`, `hasValidLicense`) are rewritten to
 *    `MOV X0,X8 ; MOV W1,#1 ; B QVariant::QVariant(bool)` — constructs `QVariant(true)` into the
 *    caller's sret buffer (a bare `MOV W0,#1 ; RET` SIGSEGVs, since the caller reads the buffer
 *    via `QVariant::isNull()`); the branch displacement is computed from the PLT address, and
 *  - `getAppVariant()`'s flag branch is NOP-ed so it always reports "plus", removing the upsell.
 *
 * Offsets are per-version (`nm -D <so> | c++filt`); entry bytes are asserted before writing.
 */

private const val LIB_PATH = "lib/arm64-v8a/libstellarium-mobile-plus_arm64-v8a.so"

private const val MOV_X0_X8 = 0xAA0803E0.toInt()      // MOV X0, X8
private const val MOV_W1_1 = 0x52800021                // MOV W1, #1

// getHasFeaturePLUS opens with a big stack frame (160 B + 12 callee saves) because the stock body
// walks a QList<QString>.
private val HAS_FEATURE_PLUS_ENTRY = intArrayOf(
    0xD10283FF.toInt(),  // SUB SP, SP, #0xA0
    0xA9047BFD.toInt(),  // STP X29, X30, [SP, #0x40]
)

// hasValidLicense is small — reads status from [this+0x18], branches on its value to one of two
// pre-built "return QVariant(true/false)" tail-calls. Patching the entry collapses the decision tree.
private val HAS_VALID_LICENSE_ENTRY = intArrayOf(
    0xB9401809.toInt(),  // LDR W9, [X0, #0x18]
    0x7100053F,          // CMP W9, #0x1
)

/** Rewrite the entry at [offset] into a tail-call returning `QVariant(true)` via the ctor at [qvariantBoolCtor]. */
private fun returnQVariantTrue(label: String, offset: Int, qvariantBoolCtor: Int, entry: IntArray) = BinaryPatch(
    label, offset,
    expected = entry,
    replacement = intArrayOf(MOV_X0_X8, MOV_W1_1, Arm64.b(pc = offset + 8, target = qvariantBoolCtor)),
)

/** `getAppVariant`: `cbz w9, <free>` → NOP, so it always falls through to "plus". */
private fun forcePlusVariant(offset: Int) =
    BinaryPatch("getAppVariant() force plus", offset, intArrayOf(0x34000089), intArrayOf(Arm64.NOP))

private fun plusPatches(qvariantBoolCtor: Int, hasFeaturePlus: Int, hasValidLicense: Int, appVariantBranch: Int) =
    listOf(
        returnQVariantTrue("StelAPI::getHasFeaturePLUS()", hasFeaturePlus, qvariantBoolCtor, HAS_FEATURE_PLUS_ENTRY),
        returnQVariantTrue("StelAPI::hasValidLicense()", hasValidLicense, qvariantBoolCtor, HAS_VALID_LICENSE_ENTRY),
        forcePlusVariant(appVariantBranch),
    )

internal val STELLARIUM_PLUS_PATCHES = mapOf(
    "1.16.2" to plusPatches(
        qvariantBoolCtor = 0x8AB690,
        hasFeaturePlus = 0x72FB00,
        hasValidLicense = 0x737B84,
        appVariantBranch = 0x736F98,
    ),
    "1.16.3" to plusPatches(
        qvariantBoolCtor = 0x8ACCE0,
        hasFeaturePlus = 0x73069C,
        hasValidLicense = 0x738798,
        appVariantBranch = 0x737BAC,
    ),
)

@Suppress("unused")
val unlockPlusPatch = patch(
    name = "Unlock Plus",
) {
    compatibleWith(COMPATIBILITY_STELLARIUM)

    dependsOn(disableAnalyticsPatch)

    execute {
        val version = packageMetadata.versionName
        val patches = STELLARIUM_PLUS_PATCHES.forVersion(
            "Stellarium", version, "Add offsets via `nm -D <so> | c++filt` (see KDoc).",
        )
        patchNativeLibrary(LIB_PATH, "Stellarium", version, patches)
    }
}
