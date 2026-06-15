package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM

/**
 * Adds a first-run prompt to download + install Stellarium's extended deep catalog (deep-sky
 * objects, mag 8–12 stars, DSS imagery). Injects `CatalogDownloader.maybePrompt(this)` at the
 * top of the main activity's `onResume()`; the downloader (in the `stellariumassetpack`
 * extension) fetches the archive and extracts it under the app's files dir. Loading is handled
 * by the nameless dependency [loadExtendedCatalogShimPatch]. Marked experimental (pulls a large
 * external file at runtime).
 */

private const val MAIN_ACTIVITY = "Lcom/stellariumlabs/stellarium/mobile/StellariumFree;"
private const val DOWNLOADER =
    "Lio/github/bholeykabhakt/extension/stellariumassetpack/CatalogDownloader;"

@Suppress("unused")
val installExtendedCatalogPatch = bytecodePatch(
    name = "Install Extended (Deep Sky) Catalog",
) {
    compatibleWith(COMPATIBILITY_STELLARIUM)
    dependsOn(loadExtendedCatalogShimPatch)

    execute {
        val activity = mutableClassDefBy(MAIN_ACTIVITY)
        val onResume = activity.methods.firstOrNull {
            it.name == "onResume" && it.parameterTypes.isEmpty() && it.returnType == "V"
        } ?: throw PatchException("$MAIN_ACTIVITY.onResume() not found")

        onResume.addInstructions(
            0,
            "invoke-static { p0 }, $DOWNLOADER->maybePrompt(Landroid/app/Activity;)V",
        )
    }
}
