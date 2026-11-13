package io.github.bholeykabhakt.patches.backdrops

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_BACKDROPS
import io.github.bholeykabhakt.patches.utils.logMatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Unlocks all Backdrops premium (Pro + every collection/mini-collection pack) by forcing the local
 * `Premium` SQLite ownership check to report "owned": emit `true` from `existPurchase`'s Rx lambda
 * ([ExistPurchaseLambdaFingerprint]) and return `TRUE` from `getPurchased` ([GetPurchasedFingerprint]).
 * Pulls in [disablePairipPatch] (survive PairIP) + [disableAnalyticsPatch] (ads are already gated on
 * `!isPro()`, so Pro drops them).
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_BACKDROPS)

    dependsOn(disablePairipPatch, disableAnalyticsPatch)

    execute {
        // Make the lambda's own result TRUE (it starts as Boolean.FALSE and only flips when the
        // Premium row exists) so every path emits "owned" through the app's own emitter call —
        // the SingleEmitter API is R8-renamed per build.
        val lambda = ExistPurchaseLambdaFingerprint.logMatch.method
        val falseLoads = lambda.implementation!!.instructions.withIndex().filter { (_, insn) ->
            insn.opcode == Opcode.SGET_OBJECT &&
                ((insn as ReferenceInstruction).reference as FieldReference).let {
                    it.definingClass == "Ljava/lang/Boolean;" && it.name == "FALSE"
                }
        }
        if (falseLoads.isEmpty()) throw PatchException("No Boolean.FALSE result in existPurchase lambda")
        falseLoads.forEach { (i, insn) ->
            lambda.replaceInstruction(
                i,
                "sget-object v${(insn as OneRegisterInstruction).registerA}, " +
                    "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;",
            )
        }

        GetPurchasedFingerprint.logMatch.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """,
        )
    }
}
