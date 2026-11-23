import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.android.tools.r8.utils.ArchiveResourceProvider
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

plugins {
    // Fails the build if the patches file uses JDK APIs that do not exist on Android.
    alias(libs.plugins.animalsniffer)
}

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

    // Android API level 26 is the minimum API the patches are compiled for (see buildAndroid).
    signature(libs.android.api.signature) { artifact { type = "signature" } }
}

animalsniffer {
    // Checking only the compiled sources would miss bundled dependencies,
    // so the whole patches file is checked by checkAndroidApi below.
    sourceSets = emptyList()
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Runs D8 on every class in the patches file (the patches and all bundled dependencies)
    // but outputs class files instead of dex, so the check sees the code as it runs on Android,
    // after D8 has replaced methods it backports such as List.of() and Optional.isEmpty().
    val desugarPatchesClasses = register("desugarPatchesClasses") {
        val patchesFile = jar.flatMap { it.archiveFile }
        val classpath = sourceSets["main"].compileClasspath
        val output = layout.buildDirectory.file("animalsniffer/patches-desugared.jar")

        inputs.file(patchesFile)
        inputs.files(classpath)
        outputs.file(output)

        doLast {
            D8.run(
                D8Command.builder()
                    // true: skip the classes.dex that buildAndroid adds to the patches file.
                    .addProgramResourceProvider(ArchiveResourceProvider.fromArchive(patchesFile.get().asFile.toPath(), true))
                    .addClasspathFiles(classpath.files.filter { it.exists() }.map { it.toPath() })
                    .setMode(CompilationMode.DEBUG)
                    .setMinApiLevel(26)
                    .setOutput(output.get().asFile.toPath(), OutputMode.ClassFile)
                    .build(),
            )
        }
    }

    val extractPatchesClasses = register<Sync>("extractPatchesClasses") {
        from(desugarPatchesClasses.map { zipTree(it.outputs.files.singleFile) }) {
            include("**/*.class")
        }
        into(layout.buildDirectory.dir("animalsniffer/patches-classes"))
    }

    val checkAndroidApi = register<AnimalSniffer>("checkAndroidApi") {
        description = "Checks that the patches file only uses APIs available on Android."
        group = "verification"

        source(extractPatchesClasses)
        classpath = sourceSets["main"].compileClasspath
        animalsnifferSignatures = configurations["signature"]
        animalsnifferClasspath = configurations["animalsniffer"]
        // Only used to show source lines in the report.
        sourcesDirs = sourceSets["main"].allSource.sourceDirectories
        reports.text.outputLocation = layout.buildDirectory.file("reports/animalsniffer/patches.text")
        reports.csv.outputLocation = layout.buildDirectory.file("reports/animalsniffer/patches.csv")
    }

    check {
        dependsOn(checkAndroidApi)
    }

    // CI builds with buildAndroid, which does not depend on check.
    named("buildAndroid") {
        dependsOn(test, checkAndroidApi)
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