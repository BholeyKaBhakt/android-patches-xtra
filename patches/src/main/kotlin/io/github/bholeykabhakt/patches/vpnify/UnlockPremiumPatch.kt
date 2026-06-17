package io.github.bholeykabhakt.patches.vpnify

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import io.github.bholeykabhakt.patches.all.disableanalytics.disableAnalyticsPatch
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_VPNIFY
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Unlocks vpnify premium (all locations, no ads, no time limit, no paywall) by forcing the two
 * subscription getters on the `vf.d` manager (`f()`/`e()`) to return true, and suppresses the
 * "Subscription activated" dialog by nulling its callback at the show site. Pulls in
 * [disablePairipPatch], [spoofSignatureHashPatch] and [disableAnalyticsPatch].
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_VPNIFY)

    dependsOn(disablePairipPatch, spoofSignatureHashPatch, disableAnalyticsPatch)

    execute {
        SubscriptionActiveFingerprint.logMatch.method.returnEarly(true)
        AutoRenewingSubscriptionFingerprint.logMatch.method.returnEarly(true)

        // Suppress the "Subscription activated!" celebration. The show site loads the
        // premium-activated dialog singleton and invokes a callback with it:
        //   iget-object vCb, p0, Lzf/c;->p:Lqj/c;   ← dialog callback (null it)
        //   if-eqz vCb, :skip
        //   sget-object vDlg, Lsf/h;->a:Lsf/h;       ← the premium-activated dialog
        //   invoke-interface {vCb, vDlg}, Lqj/c;->invoke
        // Nulling the callback makes the existing `if-eqz` skip the show. (Class/field names are
        // tied to the pinned app version — see COMPATIBILITY_VPNIFY.)
        val dialogSingleton = "Lsf/h;->a:Lsf/h;"
        val handler = mutableClassDefByOrNull("Lzf/c;")
            ?: error("vpnify: premium-dialog handler Lzf/c; not found (version mismatch?)")
        val method = handler.methods.firstOrNull { m ->
            m.implementation?.instructions?.any {
                it.opcode == Opcode.SGET_OBJECT &&
                        (it as? ReferenceInstruction)?.reference?.toString() == dialogSingleton
            } == true
        } ?: error("vpnify: premium-activated dialog show site not found")

        val count = method.implementation!!.instructions.count()
        val showIdx = (0 until count).first {
            val ins = method.getInstruction(it)
            ins.opcode == Opcode.SGET_OBJECT &&
                    (ins as ReferenceInstruction).reference.toString() == dialogSingleton
        }
        val callbackIdx = (showIdx - 1 downTo maxOf(0, showIdx - 4)).first {
            method.getInstruction(it).opcode == Opcode.IGET_OBJECT
        }
        val reg = (method.getInstruction(callbackIdx) as OneRegisterInstruction).registerA
        method.replaceInstruction(callbackIdx, BuilderInstruction11n(Opcode.CONST_4, reg, 0))
    }
}
