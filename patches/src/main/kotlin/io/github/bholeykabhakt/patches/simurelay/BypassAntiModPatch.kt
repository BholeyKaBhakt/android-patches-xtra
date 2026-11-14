package io.github.bholeykabhakt.patches.simurelay

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_SIMURELAY
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.returnEarly

/** Stock answers; the Dart side stays on the splash screen when any of them differs. */
private val STOCK_ANSWERS = mapOf(
    "isSameAppSignature" to true,
    "isSamePackage" to true,
    "isInstallerValid" to true,
    "isEmulator" to false,
    "isDebuggable" to false,
)

/** Forces each check the anti-mod channel dispatches to, found as the first `Z` call after its query name. */
internal val bypassAntiModPatch = patch {
    compatibleWith(COMPATIBILITY_SIMURELAY)

    execute {
        val instructions = AntiModHandlerFingerprint.logMatch.method.implementation!!.instructions.toList()
        val forced = STOCK_ANSWERS.mapNotNull { (query, answer) ->
            val at = instructions.indexOfFirst {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == query
            }
            if (at < 0) return@mapNotNull null
            val check = instructions.drop(at + 1).firstNotNullOfOrNull { insn ->
                ((insn as? ReferenceInstruction)?.reference as? MethodReference)
                    ?.takeIf { it.returnType == "Z" && it.definingClass != "Ljava/lang/String;" }
            } ?: throw PatchException("No check call after \"$query\"")
            mutableClassDefBy(check.definingClass).methods.single {
                it.name == check.name && it.returnType == "Z" &&
                    it.parameterTypes.map(CharSequence::toString) == check.parameterTypes.map(CharSequence::toString)
            }.returnEarly(answer)
            "${check.definingClass}->${check.name}"
        }
        if (forced.size < 4 || forced.toSet().size != forced.size) {
            throw PatchException("Unexpected anti-mod checks: $forced")
        }
    }
}
