package io.github.bholeykabhakt.patches.all.branding

/**
 * Built-in looks, pre-placed on the 108 dp adaptive canvas (432 px) so no mask clips them:
 * `<key>.webp` colour foreground, `<key>_mono.png` themed-icon silhouette, `<key>_glyph.png`
 * status-bar glyph. Shown on [background].
 */
internal class BrandingPreset(val label: String, val background: String = "#FFFFFF")

internal val BRANDING_PRESETS = linkedMapOf(
    "play_music" to BrandingPreset("Play Music"),
    "play_books" to BrandingPreset("Play Books"),
    "play_games" to BrandingPreset("Play Games"),
    "podcasts" to BrandingPreset("Podcasts"),
    "keep" to BrandingPreset("Keep Notes"),
    "fit" to BrandingPreset("Fit"),
    "translate" to BrandingPreset("Translate"),
    "news" to BrandingPreset("Google News"),
    "earth" to BrandingPreset("Earth"),
    "kaspersky" to BrandingPreset("Kaspersky"),
    "avg" to BrandingPreset("AVG AntiVirus"),
    "norton" to BrandingPreset("Norton 360"),
    "mcafee" to BrandingPreset("McAfee Security"),
    "malwarebytes" to BrandingPreset("Malwarebytes"),
    "nordvpn" to BrandingPreset("NordVPN"),
    "protonvpn" to BrandingPreset("Proton VPN"),
    "surfshark" to BrandingPreset("Surfshark"),
    "booking" to BrandingPreset("Booking.com", background = "#0C3B7C"),
    "airbnb" to BrandingPreset("Airbnb"),
)

internal fun presetImage(key: String) = asset("$key.webp")

internal fun presetMono(key: String) = asset("${key}_mono.png")

internal fun presetGlyph(key: String) = asset("${key}_glyph.png")

private fun asset(name: String): ByteArray =
    BrandingPreset::class.java.getResourceAsStream("/branding/$name")?.use { it.readBytes() }
        ?: error("missing branding/$name")
