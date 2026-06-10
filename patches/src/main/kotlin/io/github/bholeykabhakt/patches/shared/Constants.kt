package io.github.bholeykabhakt.patches.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Shared [Compatibility] objects consumed by per-app patches.
 *
 * Each [AppTarget] is the latest version verified against the patch fingerprints
 * — bumping a target version here is a promise that every patch keyed to this
 * package still applies cleanly on that version. Add new targets to the head of
 * the list (newest-first) per the [Compatibility] doc.
 */
object Constants {
    val COMPATIBILITY_SPEEDTEST = Compatibility(
        name = "Speedtest",
        packageName = "org.zwanoo.android.speedtest",
        targets = listOf(
            AppTarget(version = "7.0.3"),
        ),
    )

    val COMPATIBILITY_AUTOMATE = Compatibility(
        name = "Automate",
        packageName = "com.llamalab.automate",
        targets = listOf(
            AppTarget(version = "1.51.1"),
        ),
    )

    val COMPATIBILITY_AUTOSYNC = Compatibility(
        name = "Autosync",
        packageName = "com.ttxapps.autosync",
        targets = listOf(
            AppTarget(version = "7.5.10"),
        ),
    )

    val COMPATIBILITY_CIRCUIT_SIMULATOR = Compatibility(
        name = "PROTO - circuit simulator",
        packageName = "com.proto.circuitsimulator",
        targets = listOf(
            AppTarget(version = "1.48.0"),
        ),
    )

    val COMPATIBILITY_SKY_TONIGHT = Compatibility(
        name = "Sky Tonight",
        packageName = "com.vitotechnology.sky.tonight.map.star.walk",
        targets = listOf(
            AppTarget(version = "2.5.0"),
        ),
    )

    val COMPATIBILITY_STELLARIUM = Compatibility(
        name = "Stellarium",
        packageName = "com.noctuasoftware.stellarium_free",
        targets = listOf(
            AppTarget(version = "1.16.0", isExperimental = true),
        ),
    )
}
