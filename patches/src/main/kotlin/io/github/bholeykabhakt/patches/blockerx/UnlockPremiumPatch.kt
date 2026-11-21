package io.github.bholeykabhakt.patches.blockerx

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_BLOCKERX
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.methodsNamed
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
 * Unlocks BlockerX premium (incl. the Premium+ tier and coin-purchased features):
 *  1. Every entitlement flag getter on `BlockerXAppSharePref` ([UNLOCK_FLAGS]) returns true. Premium
 *     is a server-refreshed local cache, so forcing the getters overrides any refresh.
 *  2. The post-splash router goes straight to home: the stock onboarding dead-ends at an intro upsell
 *     whose close doesn't advance. (Permissions are still grantable from the home screen.)
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_BLOCKERX)

    execute {
        mutableClassDefBy("Lio/funswitch/blocker/utils/sharePrefUtils/BlockerXAppSharePref;")
            .methodsNamed(UNLOCK_FLAGS)
            .forEach { it.returnEarly(true) }

        RedirectToNextScreenFingerprint.logMatch.method.addInstructions(
            0,
            "invoke-static {p0}, Lio/funswitch/blocker/features/mainActivityPage/MainActivity${'$'}Companion_;" +
                    "->openMainActivityWithPassword(Landroid/content/Context;)V\n" +
                    "return-void",
        )
    }
}
