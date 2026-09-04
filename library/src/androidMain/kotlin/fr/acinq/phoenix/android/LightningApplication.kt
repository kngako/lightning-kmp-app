package fr.acinq.phoenix.android

import android.content.Context
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.utils.preferences.GlobalPrefs

interface LightningApplication {
    fun getApplicationContext(): Context

    fun getGlobalPrefs(): GlobalPrefs

    fun getPhoenixGlobal(): PhoenixGlobal
}