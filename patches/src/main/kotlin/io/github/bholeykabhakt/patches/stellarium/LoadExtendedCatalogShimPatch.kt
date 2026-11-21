package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.Method
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM

/**
 * Nameless internal dependency of [installExtendedCatalogPatch] (not user-selectable).
 *
 * Shims the Play Asset Delivery Java API so Stellarium loads its extended deep catalog from a
 * local directory: `AssetPackManager.getPackLocation("asset_pack_extended")` is short-circuited
 * to return a [ShimAssetPackLocation] under the app's files dir when the pack is present there
 * (else null, as stock). No native edits.
 */

private const val ASSET_PACK_LOCATION =
    "Lcom/google/android/play/core/assetpacks/AssetPackLocation;"
private const val ASSET_SHIM = "Lio/github/bholeykabhakt/extension/stellariumassetpack/AssetShim;"

/** Concrete `getPackLocation(String)` returning an `AssetPackLocation` (skips the abstract interface decl). */
private fun Method.isPackLocationGetter() = name == "getPackLocation" && returnType == ASSET_PACK_LOCATION &&
    parameterTypes.singleOrNull()?.toString() == "Ljava/lang/String;" && implementation != null

@Suppress("unused")
val loadExtendedCatalogShimPatch = patch {
    compatibleWith(COMPATIBILITY_STELLARIUM)
    extendWith("extensions/stellariumassetpack.mpe")

    execute {
        var patched = 0
        classDefForEach { classDef ->
            // Don't rewrite the shim/extension itself.
            if (classDef.type.startsWith("Lio/github/bholeykabhakt/extension/")) return@classDefForEach
            if (classDef.type == ASSET_PACK_LOCATION) return@classDefForEach
            if (classDef.methods.none { it.isPackLocationGetter() }) return@classDefForEach

            mutableClassDefBy(classDef.type).methods.filter { it.isPackLocationGetter() }.forEach { method ->

                // Short-circuit: return the shim location (or null) before the
                // original Play Core logic. v0 is written then returned, so it is
                // safe regardless of the method's register layout.
                method.addInstructions(
                    0,
                    """
                        invoke-static { p1 }, $ASSET_SHIM->getPackLocation(Ljava/lang/String;)$ASSET_PACK_LOCATION
                        move-result-object v0
                        return-object v0
                    """,
                )
                patched++
            }
        }
        if (patched == 0) {
            throw PatchException(
                "No concrete AssetPackManager.getPackLocation(String) found — " +
                        "Play Core layout changed; re-check the shim hook.",
            )
        }
    }
}
