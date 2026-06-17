package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_UFOVPN
import io.github.bholeykabhakt.patches.utils.logMatchAll

/**
 * Unlocks premium (all High Speed nodes, no ads, no paywall, no kill-switch) by forcing the VIP
 * expiry to the far future, so the client-side VIP flag every gate reads is always true.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_UFOVPN)

    dependsOn(disablePairipPatch, disableAnalyticsPatch)

    execute {
        // the key string is read in many coroutines; the writer is the one that *stores* it (a
        // `put(String, Number)`).
        val writer = VipExpiryWriterFingerprint.logMatchAll.map { it.method }.single { method ->
            method.implementation!!.instructions.any {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                        (it as ReferenceInstruction).reference.toString()
                            .endsWith("(Ljava/lang/String;Ljava/lang/Number;)V")
            }
        }

        // overwrite the freshly-loaded expiry with a far-future value (~year 255744366)
        val igetIdx = writer.implementation!!.instructions
            .indexOfFirst { it.opcode == Opcode.IGET_WIDE }
        if (igetIdx < 0) error("ufovpn: expiry load not found")
        val reg = (writer.getInstruction(igetIdx) as TwoRegisterInstruction).registerA
        writer.addInstructions(igetIdx + 1, "const-wide v$reg, 0x7000000000000000L")
    }
}
