package fr.acinq.phoenix.compose

import androidx.core.os.BuildCompat


actual object AppVersion {
    actual val versionName: String
        get() = "TODO:LibraryVersionName"
    actual val versionCode: String
        get() = "TODO:LibraryVersionCode"

    fun applicationId(): String {
        return "fr.acinq.phoenix.compose"
    }
}