package io.github.bholeykabhakt.patches.all.disableanalytics

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Manifest meta-data keys consumed by Firebase Analytics, Firebase Crashlytics and
// Google Analytics SDKs at init time. Keys ending in `_deactivated` use inverted
// semantics (true = off); the rest are direct (false = off).
//
// Apps embedding these SDKs sometimes ship a flag explicitly, sometimes don't —
// if it's absent the SDK falls back to "on". So this patch both:
//   (a) flips any present key to its "off" value, and
//   (b) inserts the canonical "off" set when missing.
private val FORCE_OFF_KEYS = listOf(
    "firebase_analytics_collection_deactivated" to "true",
    "firebase_analytics_collection_enabled" to "false",
    "firebase_crashlytics_collection_enabled" to "false",
    "google_analytics_default_allow_ad_storage" to "false",
    "google_analytics_default_allow_ad_user_data" to "false",
    "google_analytics_default_allow_ad_personalization_signals" to "false",
    "google_analytics_default_allow_analytics_storage" to "false",
    "google_analytics_adid_collection_enabled" to "false",
    "google_analytics_ssaid_collection_enabled" to "false",
)

private fun forcedOffValueFor(name: String): String? {
    FORCE_OFF_KEYS.firstOrNull { it.first == name }?.let { return it.second }
    if (name.startsWith("firebase_crashlytics_") ||
        name.startsWith("firebase_analytics_") ||
        name.startsWith("google_analytics_")
    ) {
        return if (name.endsWith("_deactivated")) "true" else "false"
    }
    return null
}

@Suppress("unused")
val disableAnalyticsPatch = resourcePatch(
    name = "Disable Firebase(Google) Analytics",
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
                val off = forcedOffValueFor(name) ?: continue
                element.setAttribute("android:value", off)
                existingNames += name
            }

            // Insert the canonical "off" set for any key we'd want flipped but
            // wasn't present — covers apps that ship the SDK without explicit
            // opt-out keys (SDK then defaults to on).
            FORCE_OFF_KEYS.forEach { (name, value) ->
                if (name in existingNames) return@forEach
                val element = document.createElement("meta-data")
                element.setAttribute("android:name", name)
                element.setAttribute("android:value", value)
                application.appendChild(element)
            }
        }
    }
}
