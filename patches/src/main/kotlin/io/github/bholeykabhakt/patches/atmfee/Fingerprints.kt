package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral


/** `PremiumManager.isPremiumActive()` — the no-arg boolean getter (only one in the class). */
internal object IsPremiumActiveFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/utils/PremiumManager;",
    returnType = "Z",
    parameters = emptyList(),
)

/**
 * `HomeActivity.enableCurrentLocationDialog()` — the "Location Permission Needed" nag. Anchored on the
 * layout it inflates; the id is resolved by name per build (it shifted between releases).
 */
internal object EnableCurrentLocationDialogFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "custom_current_location_enable"),
    ),
)

/** `HomeActivity.checkGpsEnable()` — the "Phone GPS Feature Needed" nag (same layout-name anchor). */
internal object CheckGpsEnableFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        resourceLiteral(ResourceType.LAYOUT, "custom_enable_gps_location_enable"),
    ),
)

/**
 * `HomeActivity.setuponCreateActivityContent()` — once-per-launch setup (anchored on the getCountries
 * URL); `getCountryData()` is injected before its `initHome()` call.
 */
internal object SetupContentFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("https://www.atmfeesaver.com/apis/getCountries"),
)
