group = "io.github.bholeykabhakt"

val patchListGenerator by sourceSets.creating

configurations[patchListGenerator.implementationConfigurationName].extendsFrom(configurations["implementation"])
configurations[patchListGenerator.runtimeOnlyConfigurationName].extendsFrom(configurations["runtimeOnly"])

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

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn("jar", patchListGenerator.classesTaskName)

        classpath = patchListGenerator.runtimeClasspath
        mainClass.set("io.github.bholeykabhakt.patches.utils.PatchListGeneratorKt")
    }
}

dependencies {
    add(patchListGenerator.implementationConfigurationName, libs.gson)
}
