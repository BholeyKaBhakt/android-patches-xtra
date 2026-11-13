package io.github.bholeykabhakt.patches.autosync

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/** "Is purchased" gate: SFTP→FTP normalise, `<product>.License` pref read, bitmask lookup. */
internal object IsAccountTypePurchasedFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string("SFTP"),
        methodCall(definingClass = "Landroid/content/SharedPreferences;", name = "getBoolean"),
        methodCall(definingClass = "Ljava/util/HashMap;", name = "get"),
    ),
)

/** `Base32(SHA1(signatures[0]))` signer-hash computer. */
internal object SignerHashComputerFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"),
)

/** `com.ttxapps.autosync.app.e.c()` — getter for the remote `badApkSig` blacklist (Gson field). */
internal object BadApkSigGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            name = "badApkSig",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        )
    ),
)

/** `com.ttxapps.autosync.app.e.d()` — getter for the remote `badUnlockCode` revocation (Gson field). */
internal object BadUnlockCodeGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            name = "badUnlockCode",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        )
    ),
)
