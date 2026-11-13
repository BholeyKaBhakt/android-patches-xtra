package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ATM_FEE_SAVER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val HOME_ACTIVITY = "Lcom/atmfee/ui/home_activity/HomeActivity;"

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
