package io.github.bholeykabhakt.patches.mtmanager

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

internal const val IS_VIP = "\u0730"

/** Network-storage protocol chooser (`l.\u06e2\u06e4\u1a75`): its SubMenu builder gates the premium protocols on one isVip(). */
internal object NetStorageChooserFingerprint : Fingerprint(
    definingClass = "Ll/\u06e2\u06e4\u1a75;",
    name = "\u05ab",
    returnType = "V",
    parameters = listOf("Landroid/view/SubMenu;"),
    filters = listOf(methodCall(name = IS_VIP, opcode = Opcode.INVOKE_STATIC)),
)
