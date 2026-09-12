package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.data.DecryptNostrKeysResult
import fr.acinq.phoenix.security.EncryptedNostrKeys
import fr.acinq.phoenix.security.JvmKeyStore
import fr.acinq.phoenix.security.KeyStoreNames
import fr.acinq.phoenix.security.keyStoreEncryption
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The half `EncryptedNostrKeysTest` cannot do: encrypt, write, read back, decrypt. The jvm
 * is the one target where the key store can be unlocked on the host, so it is where the
 * atomic write and the integrity check on read are actually exercised.
 */
class NostrKeyManagerJvmTest {

    private lateinit var storeDir: File
    private lateinit var dataDir: File

    private val first = PrivateKey(ByteVector32("01".repeat(32)))
    private val second = PrivateKey(ByteVector32("02".repeat(32)))

    private fun passphrase() = "correct horse battery staple".toCharArray()

    @BeforeTest
    fun setUp() {
        storeDir = Files.createTempDirectory("phoenix-nostr-keys-store").toFile()
        dataDir = Files.createTempDirectory("phoenix-nostr-keys-data").toFile()
        JvmKeyStore.lock()
        JvmKeyStore.unlock(passphrase(), storeDir)
    }

    @AfterTest
    fun tearDown() {
        JvmKeyStore.lock()
        storeDir.deleteRecursively()
        dataDir.deleteRecursively()
    }

    private fun keyed(vararg keys: PrivateKey) = keys.associateBy { it.nostrPublicKeyHex() }

    @Test
    fun `a missing file is not found, not an error`() {
        assertIs<DecryptNostrKeysResult.Failure.FileNotFound>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `round trips two keys by their public keys`() {
        val keys = keyed(first, second)

        NostrKeyManager.writeToDir(dataDir.toOkioPath(), EncryptedNostrKeys.encrypt(keys))
        val result = NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath())

        assertIs<DecryptNostrKeysResult.Success>(result)
        assertEquals(keys, result.keys)
        assertEquals(64, result.keys.keys.first().length, "keyed by the x-only public key, hex")
    }

    @Test
    fun `a second write replaces the file, and leaves no temporary behind`() {
        val dir = dataDir.toOkioPath()
        NostrKeyManager.writeToDir(dir, EncryptedNostrKeys.encrypt(keyed(first)))
        NostrKeyManager.writeToDir(dir, EncryptedNostrKeys.encrypt(keyed(first, second)))

        val result = NostrKeyManager.loadAndDecrypt(dir)

        assertIs<DecryptNostrKeysResult.Success>(result)
        assertEquals(keyed(first, second), result.keys)
        assertTrue(FileSystem.SYSTEM.exists(dir.resolve("nostr-keys.dat")))
        assertFalse(FileSystem.SYSTEM.exists(dir.resolve("temporary_nostr_keys.dat")))
    }

    /**
     * The file is this library's own, so an entry whose secret does not derive the public
     * key it is filed under can only be corruption -- and a key handed out under the wrong
     * identity would sign as someone the user is not.
     */
    @Test
    fun `a key filed under the wrong public key is refused as corrupt`() {
        val json = """{"${second.nostrPublicKeyHex()}":"${first.value.toHex()}"}"""
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, json.encodeToByteArray())
        NostrKeyManager.writeToDir(dataDir.toOkioPath(), EncryptedNostrKeys(iv, ciphertext))

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `json that is not a map of keys is a serialization error`() {
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, "[1,2,3]".encodeToByteArray())
        NostrKeyManager.writeToDir(dataDir.toOkioPath(), EncryptedNostrKeys(iv, ciphertext))

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `a file of another version is a serialization error, not unreadable`() {
        val file = File(dataDir, "nostr-keys.dat")
        file.writeBytes(byteArrayOf(9) + ByteArray(16) + ByteArray(32))

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `an empty file is unreadable`() {
        File(dataDir, "nostr-keys.dat").writeBytes(ByteArray(0))

        assertIs<DecryptNostrKeysResult.Failure.FileUnreadable>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `a locked key store is a key store failure, and says nothing about the file`() {
        NostrKeyManager.writeToDir(dataDir.toOkioPath(), EncryptedNostrKeys.encrypt(keyed(first)))
        JvmKeyStore.lock()

        assertIs<DecryptNostrKeysResult.Failure.KeyStoreFailure>(NostrKeyManager.loadAndDecrypt(dataDir.toOkioPath()))
    }
}
