package io.github.bholeykabhakt.patches.inshorts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_INSHORTS

/**
 * Removes every Inshorts login screen so the app opens straight to the feed. Two surfaces:
 *
 *  - **The login card** (`OnboardingCard.Type.USP_LOGIN`) — added by
 *    `HomeActivityCardDataManager.h()` for the config card named "LOGIN"; we make `h()`
 *    early-return for that card so it is never added to the onboarding deck.
 *
 *  - **The login dialog** (`OnboardingLoginDialogFragment`) shown by the time-spend /
 *    relevancy cards when `OnboardingCardData.getLoginEnabled()` is true — forcing that
 *    getter to `false` makes those cards take their existing "skip without login" branch.
 */
@Suppress("unused")
val noLoginPatch = bytecodePatch(
    name = "Skip Login",
) {
    compatibleWith(COMPATIBILITY_INSHORTS)

    execute {
        // 1) Login card: make HomeActivityCardDataManager.h(OnboardingCardData, boolean)
        //    skip the "LOGIN" card. h() is obfuscated; find it as the method that reads the
        //    OnboardingCard.Type.USP_LOGIN enum field.
        val deck = mutableClassDefBy("Lcom/nis/app/ui/activities/HomeActivityCardDataManager;")
        val h = deck.methods.firstOrNull { m ->
            m.implementation?.instructions?.any { insn ->
                insn.opcode == Opcode.SGET_OBJECT &&
                        ((insn as? ReferenceInstruction)?.reference as? FieldReference)?.name == "USP_LOGIN"
            } == true
        }
            ?: throw PatchException("Inshorts Skip Login: onboarding deck builder (USP_LOGIN) not found")

        // `if ("LOGIN".equals(onboardingCardData.getCardName())) return;` at method entry.
        // p1 = the OnboardingCardData arg (p0 = this). .locals 3, so v0/v1 are free.
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
    }
}
