package fr.acinq.phoenix.android

import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.utils.preferences.GlobalPrefs

interface LightningApplication {
    fun getGlobalPrefs(): GlobalPrefs

    fun getPhoenixGlobal(): PhoenixGlobal
}