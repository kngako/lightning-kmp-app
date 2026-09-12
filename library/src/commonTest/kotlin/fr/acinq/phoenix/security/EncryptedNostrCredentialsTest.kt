package fr.acinq.phoenix.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers the on-disk layout of the nostr credentials file. Only the serialization is
 * exercised here: encrypting and decrypting go through the platform keystore, which isn't
 * available on the host -- `NostrCredentialManagerJvmTest` does that half, including the
 * JSON inside the ciphertext, where a keystore can be unlocked.
 *
 * This format is a compatibility contract -- changing it makes every credential already on
 * disk unreadable -- so the expected bytes are spelled out literally rather than derived
 * from the constants under test. It is the same envelope as `nostr-keys.dat`; what
 * changed is the file name and the entries, and the file name is the reason an older
 * build is not affected by the change.
 */
class EncryptedNostrCredentialsTest {

    private val iv = ByteArray(16) { it.toByte() }
    private val ciphertext = ByteArray(64) { (it * 7).toByte() }

    @Test
    fun round_trips() {
        val credentials = EncryptedNostrCredentials(iv, ciphertext)

        val decoded = EncryptedNostrCredentials.deserialize(credentials.serialize())

        assertContentEquals(iv, decoded.iv)
        assertContentEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun serialized_layout_is_stable() {
        val serialized = EncryptedNostrCredentials(iv, ciphertext).serialize()

        assertEquals(1 + iv.size + ciphertext.size, serialized.size)
        assertEquals(1.toByte(), serialized[0], "first byte must be the file version")
        assertContentEquals(iv, serialized.copyOfRange(1, 1 + iv.size))
        assertContentEquals(ciphertext, serialized.copyOfRange(1 + iv.size, serialized.size))
    }

    /** Ciphertext length varies with the number of entries, so it must not be assumed fixed. */
    @Test
    fun ciphertext_of_any_length_round_trips() {
        for (length in listOf(0, 1, 15, 16, 17, 200)) {
            val bytes = ByteArray(length) { (it + 3).toByte() }
            val decoded = EncryptedNostrCredentials.deserialize(EncryptedNostrCredentials(iv, bytes).serialize())
            assertContentEquals(bytes, decoded.ciphertext, "length $length")
        }
    }

    @Test
    fun an_unknown_version_is_refused() {
        val serialized = EncryptedNostrCredentials(iv, ciphertext).serialize()
        serialized[0] = 2

        assertFailsWith<IllegalArgumentException> { EncryptedNostrCredentials.deserialize(serialized) }
    }

    @Test
    fun a_file_shorter_than_its_iv_is_refused() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedNostrCredentials.deserialize(byteArrayOf(1, 0, 0, 0))
        }
    }

    @Test
    fun an_iv_of_the_wrong_length_cannot_be_serialized() {
        assertFailsWith<RuntimeException> { EncryptedNostrCredentials(ByteArray(12), ciphertext).serialize() }
    }
}
