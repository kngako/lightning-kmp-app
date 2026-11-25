package fr.acinq.phoenix.security

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The on-disk layout of `recovery-codes.dat`. Only the serialization is exercised here; the
 * keystore half is `RecoveryCodeManagerJvmTest`'s. The layout is a compatibility contract,
 * so the expected bytes are spelled out rather than derived from the constants under test.
 */
class EncryptedRecoveryCodesTest {

    private val iv = ByteArray(16) { it.toByte() }
    private val ciphertext = ByteArray(64) { (it * 7).toByte() }

    @Test
    fun round_trips() {
        val decoded = EncryptedRecoveryCodes.deserialize(EncryptedRecoveryCodes(iv, ciphertext).serialize())
        assertContentEquals(iv, decoded.iv)
        assertContentEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun serialized_layout_is_stable() {
        val serialized = EncryptedRecoveryCodes(iv, ciphertext).serialize()
        assertEquals(1 + iv.size + ciphertext.size, serialized.size)
        assertEquals(1.toByte(), serialized[0], "first byte must be the file version")
        assertContentEquals(iv, serialized.copyOfRange(1, 1 + iv.size))
        assertContentEquals(ciphertext, serialized.copyOfRange(1 + iv.size, serialized.size))
    }

    @Test
    fun an_unknown_version_is_refused() {
        val serialized = EncryptedRecoveryCodes(iv, ciphertext).serialize()
        serialized[0] = 2
        assertFailsWith<IllegalArgumentException> { EncryptedRecoveryCodes.deserialize(serialized) }
    }

    @Test
    fun a_file_shorter_than_its_iv_is_refused() {
        assertFailsWith<IllegalArgumentException> { EncryptedRecoveryCodes.deserialize(byteArrayOf(1, 0, 0, 0)) }
    }

    @Test
    fun an_iv_of_the_wrong_length_cannot_be_serialized() {
        assertFailsWith<RuntimeException> { EncryptedRecoveryCodes(ByteArray(12), ciphertext).serialize() }
    }
}
