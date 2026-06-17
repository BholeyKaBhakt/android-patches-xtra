package io.github.bholeykabhakt.patches.backdrops

import app.morphe.patcher.Fingerprint

/**
 * Fingerprints for Backdrops' premium gate — both reads live in `DatabaseHandlerIAB` and are anchored
 * on the **double-space** query literal `"SELECT  * FROM Premium WHERE item='"` (the write path uses a
 * single-space `"SELECT * …"`, so it never matches). See [unlockPremiumPatch].
 */

private const val SELECT_PREMIUM = "SELECT  * FROM Premium WHERE item='"

/** `existPurchase(sku)` Rx body `lambda$existPurchase$0(String, SingleEmitter)` → emits the result. */
internal object ExistPurchaseLambdaFingerprint : Fingerprint(
    definingClass = "Lcom/backdrops/wallpapers/data/local/DatabaseHandlerIAB;",
    returnType = "V",
    strings = listOf(SELECT_PREMIUM),
)

/** `getPurchased(sku)` — synchronous twin, returns boxed `Ljava/lang/Boolean;`. */
internal object GetPurchasedFingerprint : Fingerprint(
    definingClass = "Lcom/backdrops/wallpapers/data/local/DatabaseHandlerIAB;",
    name = "getPurchased",
    returnType = "Ljava/lang/Boolean;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(SELECT_PREMIUM),
)
