package io.github.bholeykabhakt.patches.simurelay

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SIMURELAY
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.requireFreeRegisters

/** Play product ids; the Dart side unlocks a feature when `purchased_<id>` is true and never clears it. */
private val PRODUCT_IDS = listOf("premium_id", "timersproduct", "toolsproduct")

/** Writes the purchase flags into the Flutter preferences before the Dart entrypoint loads them. */
@Suppress("unused")
val unlockAllFeaturesPatch = patch(
    name = "Unlock All Features",
) {
    compatibleWith(COMPATIBILITY_SIMURELAY)

    dependsOn(bypassAntiModPatch)

    execute {
        val method = ConfigureFlutterEngineFingerprint.logMatch.method
        method.requireFreeRegisters(3)
        method.addInstructions(
            0,
            buildString {
                appendLine("const-string v0, \"FlutterSharedPreferences\"")
                appendLine("const/4 v1, 0x0")
                appendLine(
                    "invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences" +
                        "(Ljava/lang/String;I)Landroid/content/SharedPreferences;",
                )
                appendLine("move-result-object v0")
                appendLine(
                    "invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()" +
                        "Landroid/content/SharedPreferences\$Editor;",
                )
                appendLine("move-result-object v0")
                appendLine("const/4 v1, 0x1")
                PRODUCT_IDS.forEach { id ->
                    appendLine("const-string v2, \"flutter.purchased_$id\"")
                    appendLine(
                        "invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences\$Editor;->putBoolean" +
                            "(Ljava/lang/String;Z)Landroid/content/SharedPreferences\$Editor;",
                    )
                }
                appendLine("invoke-interface {v0}, Landroid/content/SharedPreferences\$Editor;->apply()V")
            },
        )
    }
}
