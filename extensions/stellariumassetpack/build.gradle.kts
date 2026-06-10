extension {
    name = "extensions/stellariumassetpack.mpe"
}

android {
    namespace = "io.github.bholeykabhakt.extension.stellariumassetpack"
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
    // Provided by the target app at runtime — compile against it only so we can
    // subclass AssetPackLocation; do NOT bundle it into the .mpe.
    compileOnly("com.google.android.play:core:1.10.3")
}
