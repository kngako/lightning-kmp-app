/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.managers

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.acinq.bitcoin.Chain
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.preferences.GlobalPrefs
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path
import okio.SYSTEM

expect fun computePreferencePath(
    platformContext: PlatformContext,
    dataStoreFileName: String,
): Path

class DataStoreManager(
    private val ctx: PlatformContext,
    private val chain: Chain,
) {

    constructor(business: PhoenixBusiness): this(
        ctx = business.phoenixGlobal.ctx,
        chain = business.chain,
    )

    fun loadGlobalPrefsForWallet(): GlobalPrefs {
        val existingGlobalPrefs = _globalPrefsFlow.value
        if (existingGlobalPrefs != null) return existingGlobalPrefs

        val newGlobalPrefs = PreferenceDataStoreFactory.createWithPath {
            computePreferencePath(
                platformContext = ctx,
                dataStoreFileName = "globalprefs.preferences_pb",
            )
        }.let { GlobalPrefs(it) }

        _globalPrefsFlow.value = newGlobalPrefs

        return newGlobalPrefs
    }

    fun loadUserPrefsForWallet(walletId: WalletId): UserPrefs {
        val existingUserPrefs = _userPrefsMapFlow.value[walletId]
        if (existingUserPrefs != null) return existingUserPrefs

        val newUserPrefs = PreferenceDataStoreFactory.createWithPath {
            computePreferencePath(
                platformContext = ctx,
                dataStoreFileName = "userprefs_${walletId.nodeIdHash}.preferences_pb",
            )
        }.let { UserPrefs(it) }

        val newUserPrefsMap = _userPrefsMapFlow.value.toMutableMap()
        newUserPrefsMap[walletId] = newUserPrefs
        _userPrefsMapFlow.value = newUserPrefsMap

        return newUserPrefs
    }

    fun loadInternalPrefsForWallet(walletId: WalletId): InternalPrefs {
        val existingInternalPrefs = _internalPrefsMapFlow.value[walletId]
        if (existingInternalPrefs != null) return existingInternalPrefs

        val newInternalPrefs = PreferenceDataStoreFactory.createWithPath {
            computePreferencePath(
                platformContext = ctx,
                dataStoreFileName = "internalprefs_${walletId.nodeIdHash}.preferences_pb",
            )
        }.let { InternalPrefs(it) }

        val newInternalPrefsMap = _internalPrefsMapFlow.value.toMutableMap()
        newInternalPrefsMap[walletId] = newInternalPrefs
        _internalPrefsMapFlow.value = newInternalPrefsMap

        return newInternalPrefs
    }

    /** Deletes the preferences files for a given wallet id, and removes the preferences from the map flow. */
    fun deleteNodeUserPrefs(id: WalletId): Boolean {
        val userPrefsPath = userPrefsPath(id)
        val userPrefsFileDeleted = try {
            FileSystem.SYSTEM.delete(
                path = userPrefsPath,
                mustExist = true
            )
            true
        } catch (e: IOException) {
            false
        }

        val internalPrefsPath = internalPrefsFile(id)
        val internalPrefsFileDeleted = try {
            FileSystem.SYSTEM.delete(internalPrefsPath)
            true
        } catch(e: IOException) {
            false
        }

        return userPrefsFileDeleted && internalPrefsFileDeleted
    }

    private fun userPrefsPath(id: WalletId): Path {
        return computePreferencePath(
            platformContext = ctx,
            dataStoreFileName = "userprefs_${id.nodeIdHash}.preferences_pb",
        )
    }
    private fun internalPrefsFile(id: WalletId): Path {
        return computePreferencePath(
            platformContext = ctx,
            dataStoreFileName = "internalprefs_${id.nodeIdHash}.preferences_pb",
        )
    }

    companion object {
        // maps of: (wallet_id -> userPrefs) and (wallet_id -> internalPrefs)
        private val _globalPrefsFlow = MutableStateFlow<GlobalPrefs?>(null)
        private val _userPrefsMapFlow = MutableStateFlow<Map<WalletId, UserPrefs>>(emptyMap())
        private val _internalPrefsMapFlow = MutableStateFlow<Map<WalletId, InternalPrefs>>(emptyMap())
    }
}