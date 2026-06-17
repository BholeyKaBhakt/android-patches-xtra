package io.github.bholeykabhakt.patches.backdrops

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_BACKDROPS
import io.github.bholeykabhakt.patches.utils.logMatch

/**
 * Unlocks all Backdrops premium (Pro + every collection/mini-collection pack) by forcing the local
 * `Premium` SQLite ownership check to report "owned": emit `true` from `existPurchase`'s Rx lambda
 * ([ExistPurchaseLambdaFingerprint]) and return `TRUE` from `getPurchased` ([GetPurchasedFingerprint]).
 * Pulls in [disablePairipPatch] (survive PairIP) + [disableAnalyticsPatch] (ads are already gated on
 * `!isPro()`, so Pro drops them).
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_BACKDROPS)

    dependsOn(disablePairipPatch, disableAnalyticsPatch)

    execute {
        val lambda = ExistPurchaseLambdaFingerprint.logMatch.method
        val emitter = lambda.parameterTypes[1]
        lambda.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                invoke-interface {p2, v0}, $emitter->onSuccess(Ljava/lang/Object;)V
                return-void
            """,
        )

        GetPurchasedFingerprint.logMatch.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """,
        )
    }
}
