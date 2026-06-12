extension {
    name = "extensions/stellariumassetpack.mpe"
}

android {
    namespace = "io.github.bholeykabhakt.extension.stellariumassetpack"
    // Match the host (Stellarium) minSdk; the morphe plugin defaults extensions to 23,
    // which makes lint flag APIs (e.g. getContentLengthLong, API 24) it can never hit.
    defaultConfig {
        minSdk = 28
    }
    // compileOnly Play Core is never shipped; ignore its deprecation lint.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // compileOnly: only to subclass AssetPackLocation; never bundled — the host provides it
    // at runtime. Matches the artifact/version Stellarium 1.16.2 actually ships (the new
    // split, not the deprecated monolithic `com.google.android.play:core`).
    compileOnly("com.google.android.play:asset-delivery:2.3.0")
}
