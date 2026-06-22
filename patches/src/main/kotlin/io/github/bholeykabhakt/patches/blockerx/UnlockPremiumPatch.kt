package io.github.bholeykabhakt.patches.blockerx

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_BLOCKERX
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/** Every `BlockerXAppSharePref` flag a feature gates on — base Premium, the Premium+ tier, the
 *  per-mode premiums, and the coin-purchased unlocks (logo / block-screen / app-name = Private Mode). */
private val UNLOCK_FLAGS = setOf(
    "getSUB_STATUS", "getSUB_STATUS_LITE",
    "getIS_ACTIVE_PREMIUM_PLUS",
    "getIS_ACTIVE_CODI_MODE_PREMIUM", "getIS_ACTIVE_DESKTOP_PREMIUM",
    "getIS_ACTIVE_ED_COURSE_PREMIUM", "getIS_ACTIVE_URGES_MODE_PREMIUM",
    "getIS_COINS_PURCHASED_FOR_LOGO", "getIS_COINS_PURCHASED_FOR_CUSTOMIZE_BLOCK_SCREEN",
    "getIS_COINS_PURCHASED_FOR_APP_NAME", "getIS_COINS_PURCHASED_FOR_SILENCE_NOTIFICATIONS",
    "getIS_DONATION_PURCHASED",
)

/**
 * Unlocks BlockerX premium (incl. the Premium+ tier + coin-purchased features) and makes the
 * re-signed build usable:
 *
 *  1. Forces every entitlement flag getter on `BlockerXAppSharePref` ([UNLOCK_FLAGS]) to return true.
 *     `getSUB_STATUS()`/`…_LITE()` are the base-premium chokepoint (92+17 gates); `IS_ACTIVE_PREMIUM_PLUS`
 *     + the per-mode/coin flags gate the higher-tier and disguise features (Private Mode logo, custom
 *     block screen, …). DexGuard-obfuscated but no re-sign RASP, and these getters are dead
 *     control-flow junk with no integrity guard. Premium is a server-refreshed local cache, so
 *     forcing the getters overrides any refresh.
 *  2. Routes `CommonUtils.redirectFromAcceptTremToNextScreen(Context)` straight to home via
 *     `MainActivity.openMainActivityWithPassword(it)` — the stock onboarding dead-ends at an intro
 *     upsell whose close doesn't advance, so a fresh re-signed install never reaches MainActivity
 *     otherwise. (Permissions are still grantable from the home screen.)
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_BLOCKERX)

    execute {
        val pref =
            mutableClassDefBy("Lio/funswitch/blocker/utils/sharePrefUtils/BlockerXAppSharePref;")
        val patched = pref.methods.filter { it.name in UNLOCK_FLAGS && it.returnType == "Z" }
        check(patched.size == UNLOCK_FLAGS.size) {
            "BlockerX: expected ${UNLOCK_FLAGS.size} flag getters, found ${patched.size} " +
                    "(version mismatch?) — ${patched.map { it.name }}"
        }
        patched.forEach { it.returnEarly(true) }

        RedirectToNextScreenFingerprint.logMatch.method.addInstructions(
            0,
            "invoke-static {p0}, Lio/funswitch/blocker/features/mainActivityPage/MainActivity${'$'}Companion_;" +
                    "->openMainActivityWithPassword(Landroid/content/Context;)V\n" +
                    "return-void",
        )
    }
}
