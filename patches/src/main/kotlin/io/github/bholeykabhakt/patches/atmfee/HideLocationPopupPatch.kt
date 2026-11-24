package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly
import io.github.bholeykabhakt.patches.utils.requireMethod

/** No-ops the location/GPS nags and loads the country list once at startup instead. */
@Suppress("unused")
val hideLocationPopupPatch = patch(
    name = "Hide Location Permission Popup",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    execute {
        EnableCurrentLocationDialogFingerprint.logMatch.method.returnEarly()
        CheckGpsEnableFingerprint.logMatch.method.returnEarly()

        // The suppressed dialogs were the only trigger for the country list.
        val setup = SetupContentFingerprint.logMatch
        // getCountryData() is `private final` (invoke-direct), unlike the public initHome().
        requireMethod(HOME_ACTIVITY, "getCountryData", emptyList(), "V", isStatic = false)
        setup.method.addInstructions(
            setup.instructionMatches.first().index,
            "invoke-direct {p0}, $HOME_ACTIVITY->getCountryData()V",
        )
    }
}
