package io.github.bholeykabhakt.patches.all.disablefacebooktracking

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Facebook SDK reads these meta-data values at init. Default behaviour for both is
// "on" — apps embedding the SDK have to explicitly opt out. Flip them to "false"
// to disable automatic in-app event logging and the advertiser-ID lookup.
private val FACEBOOK_TRACKING_KEYS = setOf(
    "com.facebook.sdk.AutoLogAppEventsEnabled",
    "com.facebook.sdk.AdvertiserIDCollectionEnabled",
)

@Suppress("unused")
val disableFacebookTrackingPatch = resourcePatch(
    name = "Disable Facebook SDK Tracking",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document
                .getElementsByTagName("application")
                .item(0) as Element

            val metaDataNodes = application.getElementsByTagName("meta-data")
            val existingNames = mutableSetOf<String>()

            for (i in 0 until metaDataNodes.length) {
                val element = metaDataNodes.item(i) as Element
                val name = element.getAttribute("android:name")
                if (name in FACEBOOK_TRACKING_KEYS) {
                    element.setAttribute("android:value", "false")
                    existingNames += name
                }
            }

            // Insert missing keys so the patch covers apps that ship Facebook SDK
            // without setting the values explicitly (in which case SDK defaults to on).
            (FACEBOOK_TRACKING_KEYS - existingNames).forEach { name ->
                val element = document.createElement("meta-data")
                element.setAttribute("android:name", name)
                element.setAttribute("android:value", "false")
                application.appendChild(element)
            }
        }
    }
}
