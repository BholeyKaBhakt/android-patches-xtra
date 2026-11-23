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
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "7.1.4"),
            AppTarget(version = "7.0.4"),
        ),
    )

    val COMPATIBILITY_AUTOMATE = Compatibility(
        name = "Automate",
        packageName = "com.llamalab.automate",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "1.54.0-play"),
            AppTarget(version = "1.51.1"),
        ),
    )

    val COMPATIBILITY_AUTOSYNC = Compatibility(
        name = "Autosync",
        packageName = "com.ttxapps.autosync",
        targets = listOf(
            AppTarget(version = "7.8.3"),
        ),
    )

    val COMPATIBILITY_CIRCUIT_SIMULATOR = Compatibility(
        name = "PROTO - circuit simulator",
        packageName = "com.proto.circuitsimulator",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "1.53.0"),
            AppTarget(version = "1.49.0"),
            AppTarget(version = "1.48.0"),
        ),
    )

    val COMPATIBILITY_MT_MANAGER = Compatibility(
        name = "MT Manager",
        packageName = "bin.mt.plus",
        targets = listOf(
            AppTarget(version = "2.26.11", minSdk = 28, versionCode = 26110366),
        ),
    )

    val COMPATIBILITY_SKY_TONIGHT = Compatibility(
        name = "Sky Tonight",
        packageName = "com.vitotechnology.sky.tonight.map.star.walk",
        targets = listOf(
            AppTarget(version = "2.6.0"),
            AppTarget(version = "2.5.3"),
        ),
    )

    val COMPATIBILITY_STAR_WALK_2 = Compatibility(
        name = "Star Walk 2",
        packageName = "com.vitotechnology.StarWalk2Free",
        targets = listOf(
            AppTarget(version = "2.22.0"),
        ),
    )

    val COMPATIBILITY_COSMIC_WATCH = Compatibility(
        name = "Cosmic Watch",
        packageName = "com.cosmic_watch.cosmicwatch",
        targets = listOf(
            AppTarget(version = "3.0.1"),
        ),
    )

    val COMPATIBILITY_SOLAR_WALK_2 = Compatibility(
        name = "Solar Walk 2",
        packageName = "com.vitotechnology.SolarWalk2Free",
        targets = listOf(
            AppTarget(version = "1.6.9"),
        ),
    )

    val COMPATIBILITY_ATM_FEE_SAVER = Compatibility(
        name = "ATM Fee Saver",
        packageName = "com.atmfee",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "3.8"),
            AppTarget(version = "3.7.9"),
        ),
    )

    val COMPATIBILITY_INSHORTS = Compatibility(
        name = "Inshorts",
        packageName = "com.nis.app",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "6.13.04"),
            AppTarget(version = "6.12.18"),
            AppTarget(version = "6.12.15"),
        ),
    )

    val COMPATIBILITY_VPNIFY = Compatibility(
        name = "vpnify",
        packageName = "com.vpn.free.hotspot.secure.vpnify",
        targets = listOf(
            AppTarget(version = "2.2.9", versionCode = 2290052),
        ),
    )

    val COMPATIBILITY_STELLARIUM = Compatibility(
        name = "Stellarium",
        packageName = "com.noctuasoftware.stellarium_free",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "1.16.10"),
            AppTarget(version = "1.16.9"),
            AppTarget(version = "1.16.8"),
        ),
    )

    val COMPATIBILITY_UFOVPN = Compatibility(
        name = "UFO VPN",
        packageName = "com.ufovpn.connect.velnet",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "2.5.8"),
            AppTarget(version = "2.5.5"),
        ),
    )

    val COMPATIBILITY_BACKDROPS = Compatibility(
        name = "Backdrops",
        packageName = "com.backdrops.wallpapers",
        targets = listOf(
            AppTarget(version = "6.6"),
        ),
    )

    val COMPATIBILITY_SOLID_EXPLORER = Compatibility(
        name = "Solid Explorer",
        packageName = "pl.solidexplorer2",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "3.6.5"),
            AppTarget(version = "3.6.4"),
        ),
    )

    val COMPATIBILITY_BLOCKERX = Compatibility(
        name = "BlockerX",
        packageName = "io.funswitch.blocker",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "5.0.67"),
        ),
    )

    val COMPATIBILITY_ZANGI = Compatibility(
        name = "Zangi",
        packageName = "com.beint.zangi",
        targets = listOf(
            AppTarget(version = "6.6.4"),
        ),
    )

    val COMPATIBILITY_EVERYCIRCUIT = Compatibility(
        name = "EveryCircuit",
        packageName = "com.everycircuit.free",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "2.31"),
        ),
    )

    val COMPATIBILITY_SIMURELAY = Compatibility(
        name = "Simurelay",
        packageName = "com.mordiastudio.simurelayfree",
        targets = listOf(
            AppTarget(version = null, isExperimental = true),
            AppTarget(version = "3.0.6"),
        ),
    )
}
