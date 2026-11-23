package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
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
        val match = VipExpiryWriterFingerprint.logMatch
        val writer = match.method

        // Overwrite the freshly-loaded expiry with a far-future value (~year 255744366). The load is the
        // last iget-wide before the put("vip_expiration_time", Long.valueOf(expiry)) the fingerprint
        // matched: in a coroutine body an earlier iget-wide may just be a spilled local.
        val put = match.instructionMatches.first().index
        val igetIdx = (put downTo 0).firstOrNull { writer.getInstruction(it).opcode == Opcode.IGET_WIDE }
            ?: throw PatchException("ufovpn: no expiry load before the vip_expiration_time write")
        val reg = (writer.getInstruction(igetIdx) as TwoRegisterInstruction).registerA
        writer.addInstructions(igetIdx + 1, "const-wide v$reg, 0x7000000000000000L")
    }
}
