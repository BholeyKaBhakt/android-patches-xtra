package io.github.bholeykabhakt.patches.inshorts

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_INSHORTS

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
        val deck = mutableClassDefBy("Lcom/nis/app/ui/activities/HomeActivityCardDataManager;")
        val h = deck.methods.firstOrNull { m ->
            m.implementation?.instructions?.any { insn ->
                insn.opcode == Opcode.SGET_OBJECT &&
                        ((insn as? ReferenceInstruction)?.reference as? FieldReference)?.name == "USP_LOGIN"
            } == true
        }
            ?: throw PatchException("Inshorts Skip Login: onboarding deck builder (USP_LOGIN) not found")

        // p1 = OnboardingCardData; .locals 3, so v0/v1 are free.
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
        val getLoginEnabled = mutableClassDefBy(
            "Lcom/nis/app/network/models/onboarding/onboardingconfig/OnboardingCardData;",
        ).methods.firstOrNull { it.name == "getLoginEnabled" }
            ?: throw PatchException("Inshorts Skip Login: getLoginEnabled() not found")

        getLoginEnabled.addInstructions(
            0,
            "sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;\nreturn-object v0",
        )

        // 3) Relevancy cards (6.13+): take the signed-in branch when laying out the card buttons,
        //    which hides "Login & Save My Preferences".
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(CARD_VIEWS)) return@classDefForEach
            if (classDef.methods.none { it.hasString(LOGIN_SAVE_BUTTON) && it.callsSignedIn() }) return@classDefForEach
            mutableClassDefBy(classDef.type).methods
                .filter { it.hasString(LOGIN_SAVE_BUTTON) && it.callsSignedIn() }
                .forEach { method ->
                    val insns = method.implementation!!.instructions
                    insns.indices.filter { insns[it].isSignedInCall() }.reversed().forEach { i ->
                        val result = insns[i + 1] as OneRegisterInstruction // move-result
                        method.addInstructions(i + 2, "const/4 v${result.registerA}, 0x1")
                    }
                }
        }
    }
}

private const val CARD_VIEWS = "Lcom/nis/app/ui/customView/cardView/"
private const val LOGIN_SAVE_BUTTON = "buttonLoginSaveRelevancy"
private const val SIGNED_IN = "Lcom/nis/app/utils/FirebaseUserUtils;"

private fun Instruction.isSignedInCall() = opcode == Opcode.INVOKE_STATIC &&
    ((this as ReferenceInstruction).reference as MethodReference).let {
        it.definingClass == SIGNED_IN && it.name == "c" && it.returnType == "Z" && it.parameterTypes.isEmpty()
    }

private fun Method.callsSignedIn() = implementation?.instructions?.any { it.isSignedInCall() } == true

private fun Method.hasString(value: String) = implementation?.instructions?.any {
    (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
        ((it as ReferenceInstruction).reference as StringReference).string == value
} == true
