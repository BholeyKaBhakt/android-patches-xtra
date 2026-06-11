package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val HOME_ACTIVITY = "Lcom/atmfee/ui/home_activity/HomeActivity;"

/**
 * Suppresses the two recurring location nags, and restores the country list they
 * were (surprisingly) responsible for loading.
 *
 * The nags:
 *  - "Location Permission Needed" (`enableCurrentLocationDialog()`), shown when the
 *    runtime location permission is denied.
 *  - "Phone GPS Feature Needed" (`checkGpsEnable()`), shown when device location
 *    services are off.
 *
 * Both fire from the tab/interaction handlers (guarded by `isLocationPermission` /
 * `isGPSEnabled`), so once the user declines they re-appear on every tab switch, even
 * after picking a country by hand. Forcing an early `return-void` removes them.
 *
 * The catch: for a not-logged-in / location-denied user, the country dropdown is
 * **only** populated by those dialogs' "Not now" handlers, which call `getCountryData()`
 * (`onRequestPermissionsResult`'s denial branch has no other loader). Simply no-op'ing
 * the dialogs leaves the country list empty. We can't move the load into the dialog
 * methods — they run on every tab switch and `getCountryData()` enqueues a fresh
 * network request with no dedup, so that would spam the endpoint.
 *
 * Instead we inject a single `getCountryData()` into `setuponCreateActivityContent()`
 * (the once-per-`onCreate` setup), placed right before its existing `initHome()` call —
 * after `countryViewModel` is constructed in that same method, mirroring the app's own
 * `getCountryData(); initHome()` happy-path sequence. The list then loads once at
 * startup for everyone, independent of login/permission.
 */
@Suppress("unused")
val hideLocationPopupPatch = bytecodePatch(
    name = "Hide Location Permission Popup",
) {
    compatibleWith(COMPATIBILITY_ATM_FEE_SAVER)

    execute {
        EnableCurrentLocationDialogFingerprint.logMatch.method.returnEarly()
        CheckGpsEnableFingerprint.logMatch.method.returnEarly()

        // Compensate: load the country list once at startup, since the suppressed
        // dialogs were the only trigger on the location-denied path.
        val setup = SetupContentFingerprint.logMatch.method
        val impl = setup.implementation
            ?: throw PatchException("setuponCreateActivityContent() has no implementation")
        val initHomeIndex = impl.instructions.indexOfFirst { insn ->
            insn.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((insn as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                        it.definingClass == HOME_ACTIVITY && it.name == "initHome"
                    } == true
        }
        if (initHomeIndex < 0) {
            throw PatchException("initHome() call not found in setuponCreateActivityContent()")
        }
        // getCountryData() is `private final` (invoke-direct), unlike the public initHome().
        setup.addInstructions(
            initHomeIndex,
            "invoke-direct {p0}, $HOME_ACTIVITY->getCountryData()V"
        )
    }
}
