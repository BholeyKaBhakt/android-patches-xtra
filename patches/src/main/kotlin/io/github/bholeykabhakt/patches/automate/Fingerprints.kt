package io.github.bholeykabhakt.patches.automate

import app.morphe.patcher.Fingerprint

internal object IsBlockLimitReachedFingerprint : Fingerprint(
    definingClass = "Lcom/llamalab/automate/AutomateService;",
    returnType = "Z",
    strings = listOf("checkPremiumAllow", "runningStatementCount", "count"),
)
