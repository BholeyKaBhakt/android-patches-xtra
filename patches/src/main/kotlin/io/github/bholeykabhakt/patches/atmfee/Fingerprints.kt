package io.github.bholeykabhakt.patches.atmfee

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal

/** Fingerprints for `com.atmfee.utils.PremiumManager` (the premium chokepoint). See [unlockPremiumPatch]. */

/** `PremiumManager.isPremiumActive()` — the no-arg boolean getter (only one in the class). */
internal object IsPremiumActiveFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/utils/PremiumManager;",
    returnType = "Z",
    parameters = emptyList(),
)

/**
 * `HomeActivity.enableCurrentLocationDialog()` — builds and shows the recurring
 * "Location Permission Needed" nag. Anchored on the one resource it inflates,
 * `R.layout.custom_current_location_enable` (id `0x7f0c0037` in v3.7.9); it is the
 * only `HomeActivity` method that references that layout. See [hideLocationPopupPatch].
 */
internal object EnableCurrentLocationDialogFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        literal(0x7f0c0037), // R.layout.custom_current_location_enable
    ),
)

/**
 * `HomeActivity.checkGpsEnable()` — shows the "Phone GPS Feature Needed" nag whenever
 * device location services are off. Same recurring trigger as the permission dialog.
 * Anchored on `R.layout.custom_enable_gps_location_enable` (id `0x7f0c003b` in v3.7.9),
 * the only `HomeActivity` method referencing it. See [hideLocationPopupPatch].
 */
internal object CheckGpsEnableFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        literal(0x7f0c003b), // R.layout.custom_enable_gps_location_enable
    ),
)

/**
 * `HomeActivity.setuponCreateActivityContent()` — the once-per-launch setup run from
 * `onCreate`. It builds `countryViewModel` (with the getCountries endpoint) and then
 * calls `initHome()`. Anchored on that unique endpoint URL. [hideLocationPopupPatch]
 * injects a one-time `getCountryData()` here so the country dropdown still populates
 * after the location dialogs (which used to lazily trigger it) are suppressed.
 */
internal object SetupContentFingerprint : Fingerprint(
    definingClass = "Lcom/atmfee/ui/home_activity/HomeActivity;",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("https://www.atmfeesaver.com/apis/getCountries"),
)
