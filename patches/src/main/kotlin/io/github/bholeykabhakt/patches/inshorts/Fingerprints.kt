package io.github.bholeykabhakt.patches.inshorts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

/** Onboarding deck builder: the card-data manager method that reads `USP_LOGIN`. */
internal object OnboardingDeckBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/nis/app/ui/activities/HomeActivityCardDataManager;",
    filters = listOf(fieldAccess(name = "USP_LOGIN", opcode = Opcode.SGET_OBJECT)),
)
