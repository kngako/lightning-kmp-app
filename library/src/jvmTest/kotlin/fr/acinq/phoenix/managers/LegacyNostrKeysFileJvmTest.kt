package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.data.DecryptNostrKeysResult
import fr.acinq.phoenix.security.EncryptedNostrKeys
import fr.acinq.phoenix.security.JvmKeyStore
import fr.acinq.phoenix.security.KeyStoreNames
import fr.acinq.phoenix.security.keyStoreEncryption
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The reader of the version-1 file, which the migration in `NostrCredentialManager`
 * depends on reading exactly as the build that wrote it did. This was
 * `NostrKeyManagerJvmTest`; the cases that wrote through the manager are gone with the
 * writer, and the file is produced here the way the migration's test produces it -- the
 * bytes of `EncryptedNostrKeys.encrypt`, written directly.
 */
class LegacyNostrKeysFileJvmTest {

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

    private fun writeV1(bytes: ByteArray) = File(dataDir, "nostr-keys.dat").writeBytes(bytes)

    @Test
    fun `a missing file is not found, not an error`() {
        assertIs<DecryptNostrKeysResult.Failure.FileNotFound>(LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `reads two keys by their public keys`() {
        val keys = keyed(first, second)
        writeV1(EncryptedNostrKeys.encrypt(keys).serialize())

        val result = LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath())

        assertIs<DecryptNostrKeysResult.Success>(result)
        assertEquals(keys, result.keys)
        assertEquals(64, result.keys.keys.first().length, "keyed by the x-only public key, hex")
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
        writeV1(EncryptedNostrKeys(iv, ciphertext).serialize())

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `json that is not a map of keys is a serialization error`() {
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, "[1,2,3]".encodeToByteArray())
        writeV1(EncryptedNostrKeys(iv, ciphertext).serialize())

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `a file of another version is a serialization error, not unreadable`() {
        writeV1(byteArrayOf(9) + ByteArray(16) + ByteArray(32))

        assertIs<DecryptNostrKeysResult.Failure.SerializationError>(LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath()))
    }

    @Test
    fun `an empty file is unreadable`() {
        writeV1(ByteArray(0))

        assertIs<DecryptNostrKeysResult.Failure.FileUnreadable>(LegacyNostrKeysFile.loadAndDecrypt(dataDir.toOkioPath()))
    }
}
