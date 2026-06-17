package io.github.bholeykabhakt.patches.solidexplorer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLID_EXPLORER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val LICENSE_STATE = "Lpl/solidexplorer/licensing/LicenseState;"

/**
 * Unlocks Solid Explorer Pro: forces the cached `LicenseState` to `PREMIUM_PRO` at its only write
 * site (every license gate reads it back) and marks à-la-carte IAP products (color schemes / icon
 * sets) owned.
 */
@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
) {
    compatibleWith(COMPATIBILITY_SOLID_EXPLORER)

    execute {
        // Force the LicenseState field to PREMIUM_PRO at the iput-object that stores it.
        val constructor = LicenseDetailsConstructorFingerprint.logMatch.method
        val writeIndex = constructor.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.IPUT_OBJECT &&
                    ((it as ReferenceInstruction).reference as FieldReference).type == LICENSE_STATE
        }
        if (writeIndex < 0) throw PatchException("LicenseState write not found in LicenseDetails.<init>")
        val valueRegister =
            (constructor.getInstruction(writeIndex) as TwoRegisterInstruction).registerA
        constructor.addInstructions(
            writeIndex,
            "sget-object v$valueRegister, $LICENSE_STATE->PREMIUM_PRO:$LICENSE_STATE",
        )

        // À-la-carte color schemes / icon sets: mark every IAP product owned.
        ProductIsPurchasedFingerprint.logMatch.method.returnEarly(true)
    }
}
