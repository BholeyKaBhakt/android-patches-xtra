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
 * Reports the local `Premium` purchases as owned (Pro + every pack) on both paths: the async
 * `existPurchase` lambda and the sync `getPurchased`. Ads are gated on `!isPro()`, so they go too.
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_BACKDROPS)

    dependsOn(disablePairipPatch, disableAnalyticsPatch)

    execute {
        // Flip the lambda's own result so it emits "owned" via the app's emitter call (its API is R8-renamed).
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
