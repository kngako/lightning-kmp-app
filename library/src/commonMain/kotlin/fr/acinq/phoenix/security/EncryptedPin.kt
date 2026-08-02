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

package fr.acinq.phoenix.security

import okio.Buffer
import okio.BufferedSource

abstract class EncryptedPin {
    abstract val name: String
    override fun toString(): String = name

    /**
     * Data is encrypted with a key from the Android Keystore. The keystore's key does not require user authentication to be used.
     * The serialized content contains the version, the IV, and the payload.
     */
    abstract class KeystoreEncrypted : EncryptedPin() {
        abstract val iv: ByteArray
        abstract val ciphertext: ByteArray

        fun decrypt(): ByteArray = keyStoreDecryption(KeyStoreNames.KEY_FOR_PINCODE_V1, iv, ciphertext)

        fun serialize(version: Int): ByteArray {
            if (iv.size != IV_LENGTH) {
                throw RuntimeException("cannot serialize $name: iv not of the correct length (${iv.size}/$IV_LENGTH)")
            }
            val array = Buffer()
            array.writeByte(version)
            array.write(iv)
            array.write(ciphertext)
            return array.readByteArray()
        }

        companion object Companion {
            const val IV_LENGTH = 16
            fun deserialize(stream: BufferedSource): Pair<ByteArray, ByteArray> {
                val iv = ByteArray(IV_LENGTH)
                stream.read(iv, 0, IV_LENGTH)
                val availableBytes = stream.buffer.size.toInt()
                val cipherText = ByteArray(availableBytes)
                stream.read(cipherText, 0, availableBytes)
                return iv to cipherText
            }
        }
    }
}