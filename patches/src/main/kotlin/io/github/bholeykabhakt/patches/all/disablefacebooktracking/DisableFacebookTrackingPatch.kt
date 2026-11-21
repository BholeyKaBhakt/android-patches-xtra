package io.github.bholeykabhakt.patches.all.disablefacebooktracking

import app.morphe.patcher.patch.document
import app.morphe.patcher.patch.patch
import io.github.bholeykabhakt.patches.utils.appendMetaData
import io.github.bholeykabhakt.patches.utils.applicationElement
import io.github.bholeykabhakt.patches.utils.elements

// Facebook SDK reads these meta-data values at init. Default behaviour for both is
// "on" — apps embedding the SDK have to explicitly opt out. Flip them to "false"
// to disable automatic in-app event logging and the advertiser-ID lookup.
private val FACEBOOK_TRACKING_KEYS = setOf(
    "com.facebook.sdk.AutoLogAppEventsEnabled",
    "com.facebook.sdk.AdvertiserIDCollectionEnabled",
)

@Suppress("unused")
val disableFacebookTrackingPatch = patch(
    name = "Disable Facebook SDK Tracking",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.applicationElement()
            val existingNames = mutableSetOf<String>()

            for (element in application.elements("meta-data")) {
                val name = element.getAttribute("android:name")
                if (name in FACEBOOK_TRACKING_KEYS) {
                    element.setAttribute("android:value", "false")
                    existingNames += name
                }
            }

            // Insert missing keys (the SDK defaults to on when a key is absent).
            (FACEBOOK_TRACKING_KEYS - existingNames).forEach { application.appendMetaData(it, "false") }
        }
    }
}
