package io.github.bholeykabhakt.patches.inshorts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnBoxedBooleanEarly
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_INSHORTS
import io.github.bholeykabhakt.patches.utils.logMatch
import io.github.bholeykabhakt.patches.utils.method
import io.github.bholeykabhakt.patches.utils.requireFreeRegisters
import io.github.bholeykabhakt.patches.utils.requireMethod

/**
 * Removes every login surface so first run lands on the feed:
 *   - the onboarding "LOGIN" card (skipped in the deck builder)
 *   - the login dialog opened by time-spend / relevancy cards (`getLoginEnabled()` → false)
 *   - 6.13+: the relevancy cards' "Login & Save My Preferences" button
 */
@Suppress("unused")
val noLoginPatch = patch(
    name = "Skip Login",
) {
    compatibleWith(COMPATIBILITY_INSHORTS)

    execute {
        // 1) Login card: skip the "LOGIN" card in the onboarding deck builder (reads USP_LOGIN).
        val h = OnboardingDeckBuilderFingerprint.logMatch.method
        // The fingerprint only pins the USP_LOGIN read; check what the injected code relies on.
        if (AccessFlags.STATIC.isSet(h.accessFlags) || h.returnType != "V" ||
            h.parameterTypes.firstOrNull()?.toString() != ONBOARDING_CARD
        ) {
            throw PatchException("Onboarding deck builder is not an instance ($ONBOARDING_CARD…)V: ${h.parameterTypes} -> ${h.returnType}")
        }
        h.requireFreeRegisters(2)
        requireMethod(ONBOARDING_CARD, "getCardName", emptyList(), "Ljava/lang/String;", isStatic = false)
        val card = mutableClassDefBy(ONBOARDING_CARD)

        h.addInstructionsWithLabels(
            0,
            """
                const-string v0, "LOGIN"
                invoke-virtual { p1 }, Lcom/nis/app/network/models/onboarding/onboardingconfig/OnboardingCardData;->getCardName()Ljava/lang/String;
                move-result-object v1
                invoke-virtual { v0, v1 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :keep
                return-void
                :keep
                nop
            """,
        )

        // 2) Login dialog: OnboardingCardData.getLoginEnabled() -> Boolean.FALSE.
        card.method("getLoginEnabled").returnBoxedBooleanEarly(false)

        // 3) Relevancy cards (6.13+): take the signed-in branch when laying out the card buttons,
        //    which hides "Login & Save My Preferences".
        var hasButton = false
        var rewrites = 0
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(CARD_VIEWS)) return@classDefForEach
            if (classDef.methods.any { it.hasString(LOGIN_SAVE_BUTTON) }) hasButton = true
            if (classDef.methods.none { it.hasString(LOGIN_SAVE_BUTTON) && it.callsSignedIn() }) return@classDefForEach
            mutableClassDefBy(classDef.type).methods
                .filter { it.hasString(LOGIN_SAVE_BUTTON) && it.callsSignedIn() }
                .forEach { method ->
                    val insns = method.implementation!!.instructions
                    insns.indices.filter { insns[it].isSignedInCall() }.reversed().forEach { i ->
                        val result = insns.getOrNull(i + 1)
                        if (result?.opcode != Opcode.MOVE_RESULT) return@forEach // result unused: nothing to force
                        val reg = (result as OneRegisterInstruction).registerA
                        method.addInstructions(i + 2, "${if (reg < 16) "const/4" else "const/16"} v$reg, 0x1")
                        rewrites++
                    }
                }
        }
        // Builds without relevancy cards have no button; with it, a miss means the layout changed.
        if (hasButton && rewrites == 0) throw PatchException("Relevancy cards: no signed-in check next to \"$LOGIN_SAVE_BUTTON\"")
    }
}

private const val ONBOARDING_CARD = "Lcom/nis/app/network/models/onboarding/onboardingconfig/OnboardingCardData;"
private const val CARD_VIEWS = "Lcom/nis/app/ui/customView/cardView/"
private const val LOGIN_SAVE_BUTTON = "buttonLoginSaveRelevancy"
// "Is signed in": the only static ()Z on FirebaseUserUtils (its name is obfuscated).
private const val SIGNED_IN = "Lcom/nis/app/utils/FirebaseUserUtils;"

private fun Instruction.isSignedInCall() = opcode == Opcode.INVOKE_STATIC &&
    ((this as ReferenceInstruction).reference as MethodReference).let {
        it.definingClass == SIGNED_IN && it.returnType == "Z" && it.parameterTypes.isEmpty()
    }

private fun Method.callsSignedIn() = implementation?.instructions?.any { it.isSignedInCall() } == true

private fun Method.hasString(value: String) = implementation?.instructions?.any {
    (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
        ((it as ReferenceInstruction).reference as StringReference).string == value
} == true
