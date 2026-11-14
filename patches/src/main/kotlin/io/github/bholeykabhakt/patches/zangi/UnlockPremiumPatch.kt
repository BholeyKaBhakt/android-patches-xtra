package io.github.bholeykabhakt.patches.zangi

import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_ZANGI
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Every premium gate (stealth mode, capture prevention, wipe/delete-for-both, original-size and
 * large file transfer, bigger groups/calls, VPN tunneling) reads `PremiumManager.isPremium()`.
 * Server-side limits (e.g. conference size, upload caps) are not affected.
 */
@Suppress("unused")
val unlockPremiumPatch = patch(
    name = "Unlock Premium",
) {
    compatibleWith(COMPATIBILITY_ZANGI)

    execute {
        IsPremiumFingerprint.logMatch.method.returnEarly(true)

        // On every signaling login the server sends premium="0"; seeing "was premium, now not", the
        // handler switches VPN tunneling off again a second after the user enables it.
        OnRtmpEventFingerprint.logMatch.method.apply {
            val index = implementation!!.instructions.indexOfFirst {
                val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                ref?.name == "setVpnConnectionType" &&
                    ref.definingClass == "Lcom/beint/project/core/httpclient/RequestHostTypeManager;"
            }
            if (index < 0) throw PatchException("VPN tunneling reset call not found")
            removeInstruction(index)
        }
    }
}
