package io.github.bholeykabhakt.patches.ufovpn

import app.morphe.patcher.Fingerprint

/** The coroutine that writes the global VIP flag from the `"vip_expiration_time"` expiry. */
internal object VipExpiryWriterFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("vip_expiration_time"),
)
