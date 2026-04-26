package io.github.bholeykabhakt.patches.autosync.integrity

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Anchors only on stable surfaces:
 *   - Java string literals embedded in the method (untouched by ProGuard/R8)
 *   - Gson DTO field names (server protocol — stable across releases)
 *   - Method signature shape (return type + parameters)
 *
 * No reference to obfuscated `Ltt/<x>;` class identifiers; those reshuffle every release.
 * Cross-checked against v7.4.0 and v7.5.10 — every fingerprint here matches both.
 */

/**
 * Static method that computes the Base32 digest of the installer signature for a given package.
 *
 * v7.4.0  →  `Ltt/us7;->a(Ljava/lang/String;)Ljava/lang/String;`
 * v7.5.10 →  `Ltt/tm7;->a(Ljava/lang/String;)Ljava/lang/String;`
 *
 * Anchored by the unique pair of literals every signer-hash implementation embeds:
 *   - the Base32 alphabet (the encoding step)
 *   - the AppContext-null guard message (the precondition check)
 */
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
