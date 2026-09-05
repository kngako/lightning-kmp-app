package fr.acinq.phoenix.security

import java.io.File
import java.nio.file.Files
import java.security.KeyStoreException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercised through `keyStoreEncryption`/`keyStoreDecryption` rather than [JvmKeyStore]
 * directly, since that pair is the contract commonMain actually calls.
 */
class JvmKeyStoreTest {

    private lateinit var dir: File
    private val seed = "witness rely rich shoot cost buffalo".toByteArray()

    private fun passphrase() = "correct horse battery staple".toCharArray()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("phoenix-keystore-test").toFile()
        JvmKeyStore.lock()
    }

    @AfterTest
    fun tearDown() {
        JvmKeyStore.lock()
        dir.deleteRecursively()
    }

    @Test
    fun `round trips a seed`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        assertContentEquals(seed, keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext))
    }

    /** `EncryptedSeed.V2.serialize` rejects anything else, so this is a hard requirement. */
    @Test
    fun `iv is exactly 16 bytes`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, _) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        assertEquals(16, iv.size)
    }

    @Test
    fun `survives a lock and unlock cycle`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)

        JvmKeyStore.lock()
        assertFalse(JvmKeyStore.isUnlocked)
        JvmKeyStore.unlock(passphrase(), dir)

        assertContentEquals(seed, keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext))
    }

    @Test
    fun `refuses to work while locked`() {
        assertFailsWith<KeyStoreException> { keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed) }
        assertFailsWith<KeyStoreException> {
            keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, ByteArray(16), seed)
        }
    }

    @Test
    fun `a wrong passphrase fails rather than returning rubbish`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        JvmKeyStore.lock()

        JvmKeyStore.unlock("not the passphrase".toCharArray(), dir)
        assertFailsWith<KeyStoreException> {
            keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext)
        }
    }

    /** The reason for GCM over the CBC android uses: a modified ciphertext must not decrypt. */
    @Test
    fun `tampering with the ciphertext is detected`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        ciphertext[0] = (ciphertext[0].toInt() xor 0x01).toByte()

        assertFailsWith<javax.crypto.AEADBadTagException> {
            keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext)
        }
    }

    /**
     * Both names must get distinct key material. Encrypting under both first matters --
     * cross-decrypting against a name that has never been used only proves the key is
     * absent, which it would be even if the two shared one.
     */
    @Test
    fun `each key name gets its own key`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (seedIv, seedCt) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        keyStoreEncryption(KeyStoreNames.KEY_FOR_PINCODE_V1, "1234".toByteArray())

        assertFailsWith<javax.crypto.AEADBadTagException> {
            keyStoreDecryption(KeyStoreNames.KEY_FOR_PINCODE_V1, seedIv, seedCt)
        }
    }

    /** Reading a name the store has never held is a distinct failure from a wrong key. */
    @Test
    fun `an unused key name reports missing material`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)

        val e = assertFailsWith<KeyStoreException> {
            keyStoreDecryption(KeyStoreNames.KEY_FOR_PINCODE_V1, iv, ciphertext)
        }
        assertTrue(e.message.orEmpty().contains("no key material"), "unexpected: ${e.message}")
    }

    /** Mirrors android's `KeystoreHelper`, which rejects any name it does not know. */
    @Test
    fun `an unknown key name is rejected`() {
        JvmKeyStore.unlock(passphrase(), dir)
        assertFailsWith<IllegalArgumentException> { keyStoreEncryption("NOT_A_KEY", seed) }
    }

    @Test
    fun `encrypting twice does not repeat the iv or the ciphertext`() {
        JvmKeyStore.unlock(passphrase(), dir)
        val (iv1, ct1) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        val (iv2, ct2) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)

        assertFalse(iv1.contentEquals(iv2), "iv repeated across two encryptions")
        assertFalse(ct1.contentEquals(ct2), "ciphertext repeated across two encryptions")
        assertContentEquals(seed, keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv1, ct1))
        assertContentEquals(seed, keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv2, ct2))
    }

    /** Two installs must not derive the same key from the same passphrase. */
    @Test
    fun `a second store uses a different salt`() {
        JvmKeyStore.unlock(passphrase(), dir)
        keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        val firstSalt = File(dir, "keystore.properties").readLines().single { it.startsWith("salt=") }
        JvmKeyStore.lock()

        val other = Files.createTempDirectory("phoenix-keystore-test-2").toFile()
        try {
            JvmKeyStore.unlock(passphrase(), other)
            keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
            val secondSalt = File(other, "keystore.properties").readLines().single { it.startsWith("salt=") }
            assertFalse(firstSalt == secondSalt, "two stores shared a salt")
        } finally {
            other.deleteRecursively()
        }
    }

    @Test
    fun `the seed never appears in the store file`() {
        JvmKeyStore.unlock(passphrase(), dir)
        keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)

        val contents = File(dir, "keystore.properties").readText()
        assertFalse(contents.contains(String(seed)), "plaintext seed found in the key store")
        assertFalse(contents.contains("correct horse"), "passphrase found in the key store")
        assertTrue(contents.contains("argon2id"), "expected the kdf to be recorded")
    }

    /**
     * A store with key material but no salt is damaged. Writing a fresh salt over it would
     * turn a file that a backup could still rescue into one whose data keys are gone.
     */
    @Test
    fun `a store with key material but no salt is not overwritten`() {
        JvmKeyStore.unlock(passphrase(), dir)
        keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, seed)
        JvmKeyStore.lock()

        val file = File(dir, "keystore.properties")
        val damaged = file.readLines().filterNot { it.startsWith("salt=") }
        file.writeText(damaged.joinToString("\n"))

        assertFailsWith<IllegalStateException> { JvmKeyStore.unlock(passphrase(), dir) }
        assertTrue(
            file.readText().contains("wrapped"),
            "the damaged store was overwritten instead of being left alone",
        )
    }

    @Test
    fun `an empty passphrase is rejected`() {
        assertFailsWith<IllegalArgumentException> { JvmKeyStore.unlock(CharArray(0), dir) }
    }
}
