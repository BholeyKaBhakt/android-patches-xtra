package io.github.bholeykabhakt.patches.autosync.integrity

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/** The static method that computes the Base32 digest of the installer signature. */
internal object SignerDigestComputerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(
        "AppContext.get() should never return null",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
    ),
)

/**
 * Getter on the remote-config DTO returning the server-supplied signer-hash blacklist.
 *
 * The DTO is Gson-deserialized from JSON; the field name `badApkSig` is part of the
 * server protocol and is stable across releases. Field is private, so only a method
 * inside the same class can `iget-object` it — only the getter does.
 */
internal object BadApkSigGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            name = "badApkSig",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT,
        ),
    ),
)

/**
 * Getter for the server-supplied unlock-code revocation value. Same reasoning as above.
 */
internal object BadUnlockCodeGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            name = "badUnlockCode",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT,
        ),
    ),
)
