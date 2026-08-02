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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Covers the on-disk layout of the seed file. Only the serialization is exercised here: encrypting and
 * decrypting go through the platform keystore, which isn't available on the host.
 *
 * This format is a compatibility contract -- changing it makes every wallet already on disk unreadable --
 * so the expected bytes are spelled out literally rather than derived from the constants under test.
 */
@Suppress("DEPRECATION")
class EncryptedSeedTest {

    private val iv = ByteArray(16) { it.toByte() }
    private val ciphertext = ByteArray(64) { (it * 7).toByte() }

    @Test
    fun multiple_seed_round_trips() {
        val seed = EncryptedSeed.V2.MultipleSeed(iv, ciphertext)

        val decoded = EncryptedSeed.deserialize(seed.serialize())

        assertIs<EncryptedSeed.V2.MultipleSeed>(decoded)
        assertContentEquals(iv, decoded.iv)
        assertContentEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun single_seed_round_trips() {
        val seed = EncryptedSeed.V2.SingleSeed(iv, ciphertext)

        val decoded = EncryptedSeed.deserialize(seed.serialize())

        assertIs<EncryptedSeed.V2.SingleSeed>(decoded)
        assertContentEquals(iv, decoded.iv)
        assertContentEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun serialized_layout_is_stable() {
        val multiple = EncryptedSeed.V2.MultipleSeed(iv, ciphertext).serialize()

        assertEquals(2 + iv.size + ciphertext.size, multiple.size)
        assertEquals(2.toByte(), multiple[0], "first byte must be the seed file version")
        assertEquals(3.toByte(), multiple[1], "second byte must be the multiple-seed version")
        assertContentEquals(iv, multiple.copyOfRange(2, 2 + iv.size))
        assertContentEquals(ciphertext, multiple.copyOfRange(2 + iv.size, multiple.size))

        val single = EncryptedSeed.V2.SingleSeed(iv, ciphertext).serialize()
        assertEquals(2.toByte(), single[0])
        assertEquals(1.toByte(), single[1], "second byte must be the single-seed version")
    }

    /** Ciphertext length varies with the number of wallets, so it must not be assumed fixed. */
    @Test
    fun round_trips_ciphertexts_of_any_length() {
        listOf(1, 15, 16, 17, 255, 4096).forEach { size ->
            val payload = ByteArray(size) { (it % 251).toByte() }
            val decoded = EncryptedSeed.deserialize(EncryptedSeed.V2.MultipleSeed(iv, payload).serialize())

            assertIs<EncryptedSeed.V2.MultipleSeed>(decoded)
            assertContentEquals(payload, decoded.ciphertext, "failed to round-trip a $size byte ciphertext")
            assertContentEquals(iv, decoded.iv, "failed to round-trip the iv for a $size byte ciphertext")
        }
    }

    @Test
    fun rejects_an_unknown_seed_file_version() {
        val serialized = EncryptedSeed.V2.MultipleSeed(iv, ciphertext).serialize()
        serialized[0] = 9

        assertFailsWith<IllegalArgumentException> { EncryptedSeed.deserialize(serialized) }
    }

    /** Version 2 was used and withdrawn; it must never be silently reinterpreted as another version. */
    @Test
    fun rejects_a_withdrawn_v2_sub_version() {
        val serialized = EncryptedSeed.V2.MultipleSeed(iv, ciphertext).serialize()
        serialized[1] = 2

        assertFailsWith<IllegalArgumentException> { EncryptedSeed.deserialize(serialized) }
    }

    @Test
    fun refuses_to_serialize_a_malformed_iv() {
        listOf(0, 8, 15, 17, 32).forEach { size ->
            assertFailsWith<RuntimeException>("an iv of $size bytes must not be serialized") {
                EncryptedSeed.V2.MultipleSeed(ByteArray(size), ciphertext).serialize()
            }
        }
    }
}
