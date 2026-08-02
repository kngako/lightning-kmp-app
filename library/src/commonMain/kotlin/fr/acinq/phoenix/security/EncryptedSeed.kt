/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.security

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.utils.MnemonicLanguage
import fr.acinq.secp256k1.Hex
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource

sealed class EncryptedSeed {

    /** Serializes an encrypted seed as a byte array. */
    abstract fun serialize(): ByteArray

    override fun toString(): String = this::class.qualifiedName ?: this::class.simpleName ?: "EncryptedSeed"

    /**
     * [V2] encrypts the seed with a SecretKey from the Keystore. The key in the Keystore does not require user authentication.
     * The content may be for a [SingleSeed] or a [MultipleSeed].
     */
    sealed class V2 : EncryptedSeed() {
        abstract val iv: ByteArray
        abstract val ciphertext: ByteArray

        @Deprecated("Obsolete, do not use. Instead use [MultipleSeed] that supports multiple wallets.")
        class SingleSeed(override val iv: ByteArray, override val ciphertext: ByteArray) : V2() {
            companion object {
                /** Returns a mnemonics from a byte array, or null if it's invalid. */
                fun toMnemonicsSafe(array: ByteArray): List<String>? {
                    return try {
                        val mnemonics = Hex.decode(array.decodeToString()).decodeToString().split(" ")
                        MnemonicCode.validate(mnemonics = mnemonics, wordlist = MnemonicLanguage.English.wordlist())
                        mnemonics
                    } catch (e: Exception) {
                        log.e("seed is invalid", e)
                        null
                    }
                }
            }
        }

        class MultipleSeed(override val iv: ByteArray, override val ciphertext: ByteArray) : V2() {
            fun decryptAndGetSeedMap(): Map<WalletId, List<String>> {
                val payload = decrypt().decodeToString()
                val json = Json.decodeFromString<Map<String, List<String>>>(payload)
                return json.map { WalletId(it.key) to it.value }.toMap()
            }
        }

        fun decrypt(): ByteArray = keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext)

        @Suppress("DEPRECATION")
        /** Serialize to a V2 ByteArray. */
        override fun serialize(): ByteArray {
            if (iv.size != IV_LENGTH) {
                throw RuntimeException("cannot serialize seed: iv not of the correct length")
            }
            val array = Buffer()
            array.writeByte(SEED_FILE_VERSION_2.toInt())
            array.writeByte(
                when (this) {
                    is SingleSeed -> SINGLE_SEED_VERSION.toInt()
                    is MultipleSeed -> MULTIPLE_SEED_VERSION.toInt()
                }
            )
            array.write(iv)
            array.write(ciphertext)
            return array.readByteArray()
        }

        companion object {
            private const val IV_LENGTH = 16
            private const val SINGLE_SEED_VERSION: Byte = 1
            // version=2 has been used and removed, do not use again.
            private const val MULTIPLE_SEED_VERSION: Byte = 3

            fun deserialize(stream: BufferedSource): V2 {
                val version = stream.readByte()
                val iv: ByteArray = ByteArray(IV_LENGTH)
                stream.read(iv, 0, IV_LENGTH)
                val remainingBytes = stream.buffer.size.toInt()
                val cipherText: ByteArray = ByteArray(remainingBytes)
                stream.read(cipherText, 0, remainingBytes)
                return when (version) {
                    SINGLE_SEED_VERSION -> SingleSeed(iv, cipherText)
                    MULTIPLE_SEED_VERSION -> MultipleSeed(iv, cipherText)
                    else -> throw IllegalArgumentException("unhandled V2 seed version=$version")
                }
            }

            fun encrypt(seedMap: Map<WalletId, List<String>>): MultipleSeed {
                val json = Json.encodeToString(
                    seedMap.map { (id, words) -> id.nodeIdHash to words }.toMap()
                )
                val ciphertext = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, json.encodeToByteArray())
                return MultipleSeed(ciphertext.first, ciphertext.second)
            }
        }
    }

    companion object {
        val log = Logger.withTag("EncryptedSeed")

        const val SEED_FILE_VERSION_2: Byte = 2

        /** Reads an array of byte and de-serializes it as an [EncryptedSeed] object. */
        fun deserialize(serialized: ByteArray): EncryptedSeed {
            val stream = Buffer().write(serialized)
            return when (val version = stream.readByte()) {
                SEED_FILE_VERSION_2 -> V2.deserialize(stream)
                else -> throw IllegalArgumentException("unhandled seed file version=$version")
            }
        }
    }
}
