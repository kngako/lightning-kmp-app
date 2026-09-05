package fr.acinq.phoenix.compose

actual object AppVersion {
    /**
     * The jar manifest is the only place a jvm build has a version to read, and it is
     * absent when running from a class directory. The android actual returns a literal
     * "TODO:LibraryVersionName"; a dev marker is at least honest about not knowing.
     */
    actual val versionName: String
        get() = AppVersion::class.java.`package`?.implementationVersion ?: "0.0.0-dev"

    /** No monotonic build number exists here the way it does on android and ios. */
    actual val versionCode: String
        get() = "0"
}
