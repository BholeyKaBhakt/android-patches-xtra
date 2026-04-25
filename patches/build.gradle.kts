group = "io.github.bholeykabhakt"

patches {
    about {
        name = "Xtra Android Patches"
        description = "Xtra Patches for Android apps"
        source = "git@github.com:bholeykabhakt/revanced-patches-xtra.git"
        author = "BholeyKaBhakt"
        contact = "bholeykabhakt@proton.me"
        website = "https://bholeykabhakt.github.io"
        license = "GNU General Public License v3.0"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}