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
 * Suppresses the two recurring location nags (`enableCurrentLocationDialog()`,
 * `checkGpsEnable()`) with an early `return-void`, and — since those dialogs were the only thing
 * loading the country list for a location-denied user — injects a single `getCountryData()` into
 * the once-per-`onCreate` setup so the list still loads.
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
