package io.github.bholeykabhakt.patches.solidexplorer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SOLID_EXPLORER
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

private const val LICENSE_STATE = "Lpl/solidexplorer/licensing/LicenseState;"

/**
 * Forces `LicenseState` to `PREMIUM_PRO` at its only write site (every license gate reads it back)
 * and marks the à-la-carte IAP products (color schemes / icon sets) owned.
 */
@Suppress("unused")
val unlockProPatch = patch(
    name = "Unlock Pro",
) {
    compatibleWith(COMPATIBILITY_SOLID_EXPLORER)

    execute {
        // Force the LicenseState field to PREMIUM_PRO at the iput-object that stores it.
        val constructor = LicenseDetailsConstructorFingerprint.logMatch
        val write = constructor.instructionMatches.first()
        val valueRegister = write.getInstruction<TwoRegisterInstruction>().registerA
        constructor.method.addInstructions(
            write.index,
            "sget-object v$valueRegister, $LICENSE_STATE->PREMIUM_PRO:$LICENSE_STATE",
        )

        // À-la-carte color schemes / icon sets: mark every IAP product owned.
        ProductIsPurchasedFingerprint.logMatch.method.returnEarly(true)
    }
}
