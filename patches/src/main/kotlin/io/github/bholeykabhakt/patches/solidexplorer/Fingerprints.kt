package io.github.bholeykabhakt.patches.solidexplorer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/** Primary ctor of `LicenseDetails` — the only write of its `LicenseState` Pro-tier field. */
internal object LicenseDetailsConstructorFingerprint : Fingerprint(
    definingClass = "Lpl/solidexplorer/licensing/LicenseDetails;",
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(type = "Lpl/solidexplorer/licensing/LicenseState;", opcode = Opcode.IPUT_OBJECT),
    ),
)

/** `SELicenseManager.Product.isPurchased()` — à-la-carte IAP (color scheme) ownership getter. */
internal object ProductIsPurchasedFingerprint : Fingerprint(
    definingClass = "Lpl/solidexplorer/licensing/SELicenseManager\$Product;",
    returnType = "Z",
    parameters = emptyList(),
)

/**
 * `SEApp.setAnalyticsEnabled(boolean)` — runtime switch for Firebase Analytics + Crashlytics
 * collection. Anchored on its `analyticsEnabled` field write (vs the sibling `(Z)V` setters).
 */
internal object SetAnalyticsEnabledFingerprint : Fingerprint(
    definingClass = "Lpl/solidexplorer/SEApp;",
    // Static: the patch overwrites p0, the flag (on an instance method that would be `this`).
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        fieldAccess(name = "analyticsEnabled", type = "Z", opcode = Opcode.SPUT_BOOLEAN),
    ),
)
