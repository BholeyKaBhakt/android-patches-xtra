extension {
    name = "extensions/zangibackup.mpe"
}

android {
    namespace = "io.github.bholeykabhakt.extension.zangibackup"
    defaultConfig {
        // The backup KDF (PBKDF2WithHmacSHA256) needs API 26; the UI refuses to run below it,
        // so lint needn't hold the code to the host's minSdk 23.
        minSdk = 26
    }
}
