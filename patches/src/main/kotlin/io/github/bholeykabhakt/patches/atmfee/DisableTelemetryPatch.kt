package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Turns off Firebase collection (manifest flags) plus what `HomeActivity` starts on its own:
 * Microsoft Clarity session replay (`initClarity`) and Firebase In-App Messaging campaigns
 * (`enableInAppMessaging`, which also fires a `user_logged_in` trigger).
 */
@Suppress("unused")
val disableTelemetryPatch = patch(
    name = "Disable Telemetry",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    dependsOn(disableAnalyticsPatch)

    execute {
        mutableClassDefBy(HOME_ACTIVITY).methods
            .filter { it.name == "initClarity" || it.name == "enableInAppMessaging" }
            .also { if (it.size != 2) throw PatchException("Clarity/in-app messaging init not found") }
            .forEach { it.returnEarly() }
    }
}
