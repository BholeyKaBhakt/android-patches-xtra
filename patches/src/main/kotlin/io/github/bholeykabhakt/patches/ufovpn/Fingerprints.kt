package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

/**
 * The coroutine that writes the global VIP flag from the `"vip_expiration_time"` expiry. Many
 * coroutines read the key; the writer is the one that stores it (`put(String, Number)`).
 */
internal object VipExpiryWriterFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("vip_expiration_time"),
    filters = listOf(
        methodCall(
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/Number;"),
            returnType = "V",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
    ),
)
