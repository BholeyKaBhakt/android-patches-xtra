package io.github.bholeykabhakt.patches.all.disableanalytics

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val disableAnalyticsPatch = resourcePatch(
    name = "Disable Firebase(Google) Analytics",
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { document ->

            val applicationNode =
                document
                    .getElementsByTagName("application")
                    .item(0) as Element

            val metadataNodes = applicationNode.getElementsByTagName("meta-data")

            val metadataSequence = (0 until metadataNodes.length).asSequence()
                .map { metadataNodes.item(it) as Element }

            val filteredMetadata = metadataSequence.filter {
                it.getAttribute("android:name")
                    .startsWith("firebase_crashlytics_") || it.getAttribute("android:name")
                    .startsWith("firebase_analytics_") || it.getAttribute("android:name")
                    .startsWith("google_analytics_")
            }

            filteredMetadata.forEach { element ->
                if (element.getAttribute("android:name").endsWith("_deactivated")) {
                    element.setAttribute(
                        "android:value",
                        "true",
                    )
                } else {
                    element.setAttribute("android:value", "false")
                }
            }
        }
    }
}
