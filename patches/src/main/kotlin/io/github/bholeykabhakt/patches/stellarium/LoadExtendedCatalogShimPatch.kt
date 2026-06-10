package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM

/**
 * Internal dependency of [installExtendedCatalogPatch] — **nameless**, so it is
 * not user-selectable on its own.
 *
 * Makes Stellarium load its extended deep catalog (mag 8–12 stars, deep DSO, DSS
 * imagery) from a local directory, with **no native binary edits**, by shimming
 * the Google Play Asset Delivery *Java* API.
 *
 * ### How
 * The native engine asks Play Core whether `asset_pack_extended` is installed:
 * `libstellarium…so` → `libplaycore.so` → JNI → `AssetPackManager.getPackLocation(String)`
 * → `AssetPackLocation` (null on a sideloaded install). When non-null, the native
 * code takes its "already downloaded" path and reads the catalog from the
 * location's `assetsPath()`.
 *
 * This patch short-circuits the concrete `getPackLocation("asset_pack_extended")`
 * to return a [ShimAssetPackLocation] pointing at
 * `/data/data/<pkg>/files/asset_pack_extended` whenever the pack is present there
 * (else null — identical to the unpatched not-installed result). libplaycore
 * calls `assetsPath()` / `packStorageMethod()` **virtually**, so the shim's values
 * win and the **unmodified** engine loads the catalog. A non-null location is all
 * the engine needs — no download-state notification has to be faked.
 *
 * ### Why the hook survives obfuscation
 * `libplaycore.so` resolves `AssetPackManager` / `getPackLocation` / `assetsPath`
 * / `packStorageMethod` by **string name** via JNI, so Play Core's consumer keep
 * rules stop R8 renaming them — the hook is version-independent (unlike `.so`
 * offsets). The shared extension is merged here via [extendWith]; the dependent
 * [installExtendedCatalogPatch] downloads the pack into that dir on first run.
 */

private const val ASSET_PACK_LOCATION =
    "Lcom/google/android/play/core/assetpacks/AssetPackLocation;"
private const val ASSET_SHIM = "Lio/github/bholeykabhakt/extension/stellariumassetpack/AssetShim;"

@Suppress("unused")
val loadExtendedCatalogShimPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_STELLARIUM)
    extendWith("extensions/stellariumassetpack.mpe")

    execute {
        var patched = 0
        classDefForEach { classDef ->
            // Don't rewrite the shim/extension itself.
            if (classDef.type.startsWith("Lio/github/bholeykabhakt/extension/")) return@classDefForEach
            if (classDef.type == ASSET_PACK_LOCATION) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef.type)
            mutableClass.methods.forEach methodLoop@{ method ->
                if (method.name != "getPackLocation") return@methodLoop
                if (method.returnType != ASSET_PACK_LOCATION) return@methodLoop
                if (method.parameterTypes.singleOrNull()
                        ?.toString() != "Ljava/lang/String;"
                ) return@methodLoop
                method.implementation ?: return@methodLoop // skip the abstract interface decl

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
