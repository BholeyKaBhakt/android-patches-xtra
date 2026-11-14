package io.github.bholeykabhakt.patches.stellarium

import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_STELLARIUM

/**
 * Adds a dialog to download, import (.zip) or remove Stellarium's extended deep catalog (deep-sky
 * objects, mag 8–12 stars, DSS imagery), shown on first run and from a launcher long-press
 * shortcut. Hooks the main activity's `onResume()` and Qt's `onNewIntent()` (the shortcut on a
 * running app). Loading is handled by the nameless dependency [loadExtendedCatalogShimPatch].
 */

private const val MAIN_ACTIVITY = "Lcom/stellariumlabs/stellarium/mobile/StellariumFree;"
private const val QT_ACTIVITY_BASE = "Lorg/qtproject/qt/android/QtActivityBase;"
private const val DOWNLOADER =
    "Lio/github/bholeykabhakt/extension/stellariumassetpack/CatalogDownloader;"

@Suppress("unused")
val installExtendedCatalogPatch = patch(
    name = "Install Extended (Deep Sky) Catalog",
    description = "Download or import the deep sky catalog. Long-press the app icon to open it again.",
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

        val onNewIntent = mutableClassDefBy(QT_ACTIVITY_BASE).methods.firstOrNull {
            it.name == "onNewIntent" && it.parameterTypes.singleOrNull()?.toString() == "Landroid/content/Intent;"
        } ?: throw PatchException("$QT_ACTIVITY_BASE.onNewIntent(Intent) not found")
        onNewIntent.addInstructions(
            0,
            "invoke-static { p0, p1 }, $DOWNLOADER->onNewIntent(Landroid/app/Activity;Landroid/content/Intent;)V",
        )
    }
}
