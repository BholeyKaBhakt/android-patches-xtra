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

dependencies {
    implementation(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("io.github.bholeykabhakt.patches.utils.PatchListGeneratorKt")
    }
}
