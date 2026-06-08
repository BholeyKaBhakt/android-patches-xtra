rootProject.name = "android-patches-xtra"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.2"
}

// Shared R8 rules across all extensions. We need R8 to strip the kotlin-stdlib
// that lands on the extension's runtime classpath (transitively via AGP) so the
// produced DEX contains only the shim classes.
configure<app.morphe.patches.gradle.SettingsExtension> {
    extensions {
        proguardFiles(rootProject.projectDir.resolve("extensions/extension.pro").toString())
    }
}
