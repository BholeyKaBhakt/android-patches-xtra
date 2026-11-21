package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_UFOVPN
import io.github.bholeykabhakt.patches.utils.logMatch

/**
 * Unlocks premium (all High Speed nodes, no ads, no paywall, no kill-switch) by forcing the VIP
 * expiry to the far future, so the client-side VIP flag every gate reads is always true.
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_UFOVPN)

    dependsOn(disablePairipPatch, disableAnalyticsPatch)

    execute {
        val writer = VipExpiryWriterFingerprint.logMatch.method

        // overwrite the freshly-loaded expiry with a far-future value (~year 255744366)
        val igetIdx = writer.implementation!!.instructions
            .indexOfFirst { it.opcode == Opcode.IGET_WIDE }
        if (igetIdx < 0) error("ufovpn: expiry load not found")
        val reg = (writer.getInstruction(igetIdx) as TwoRegisterInstruction).registerA
        writer.addInstructions(igetIdx + 1, "const-wide v$reg, 0x7000000000000000L")
    }
}
