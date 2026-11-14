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

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)

    // Unit tests for patch helpers/tables; the patcher is only provided at patch time otherwise.
    testImplementation(kotlin("test"))
    testImplementation(libs.morphe.patcher)
}

tasks {
    test {
        useJUnitPlatform()
    }

    // CI builds with buildAndroid, which would otherwise skip the tests.
    named("buildAndroid") {
        dependsOn(test)
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }

    jar {
        exclude("util/**")
    }
}