package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM

/**
 * Adds a first-run prompt to **download and install** Stellarium's extended deep
 * catalog (deep-sky objects, mag 8–12 stars, DSS nebula imagery) — no Google
 * Play, account, or root required.
 *
 * Injects a single `CatalogDownloader.maybePrompt(this)` call at the top of the
 * main activity's `onResume()`. The downloader (in the `stellariumassetpack`
 * extension): no-ops if the pack is already present; otherwise shows a dialog →
 * downloads the archive (HTTPS, % progress) → extracts to
 * `getFilesDir()/asset_pack_extended/`.
 *
 * Loading is handled by the nameless dependency [loadExtendedCatalogShimPatch],
 * which shims Play Core so the engine reads from that dir. The engine only loads
 * the pack at startup (its `data_packs_on_resume` merely "updates info", it does
 * not re-run the loader), so after a successful download the dialog offers
 * **"Restart now"** to relaunch and load.
 *
 * Marked **experimental**: it pulls a large external file at runtime, so the
 * download host/availability is outside the patch's control. Configure the URL /
 * SHA-256 by editing `CatalogDownloader.configUrl()` / `configSha()` and rebuilding.
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
