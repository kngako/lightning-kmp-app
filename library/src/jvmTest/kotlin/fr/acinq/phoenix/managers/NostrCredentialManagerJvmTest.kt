package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.data.DecryptNostrCredentialsResult
import fr.acinq.phoenix.security.EncryptedNostrCredentials
import fr.acinq.phoenix.security.EncryptedNostrKeys
import fr.acinq.phoenix.security.JvmKeyStore
import fr.acinq.phoenix.security.KeyStoreNames
import fr.acinq.phoenix.security.NostrCredential
import fr.acinq.phoenix.security.keyStoreDecryption
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
 * The half `EncryptedNostrCredentialsTest` cannot do: encrypt, write, read back, decrypt.
 * The jvm is the one target where the key store can be unlocked on the host, so it is
 * where the atomic write, the JSON inside the ciphertext, the integrity checks on read,
 * and the migration from the version-1 file are actually exercised.
 */
class NostrCredentialManagerJvmTest {

    private lateinit var storeDir: File
    private lateinit var dataDir: File

    private val first = PrivateKey(ByteVector32("01".repeat(32)))
    private val second = PrivateKey(ByteVector32("02".repeat(32)))
    private val third = PrivateKey(ByteVector32("03".repeat(32)))

    private fun passphrase() = "correct horse battery staple".toCharArray()

    @BeforeTest
    fun setUp() {
        storeDir = Files.createTempDirectory("phoenix-nostr-credentials-store").toFile()
        dataDir = Files.createTempDirectory("phoenix-nostr-credentials-data").toFile()
        JvmKeyStore.lock()
        JvmKeyStore.unlock(passphrase(), storeDir)
    }

    @AfterTest
    fun tearDown() {
        JvmKeyStore.lock()
        storeDir.deleteRecursively()
        dataDir.deleteRecursively()
    }

    private val dir get() = dataDir.toOkioPath()

    private fun secret(key: PrivateKey) = key.nostrPublicKeyHex() to NostrCredential.Secret(key)
    private fun public(key: PrivateKey) = key.nostrPublicKeyHex() to NostrCredential.Public

    private fun writeRaw(json: String) {
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, json.encodeToByteArray())
        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials(iv, ciphertext))
    }

    private fun readJson(): String {
        val bytes = File(dataDir, "nostr-credentials.dat").readBytes()
        val file = EncryptedNostrCredentials.deserialize(bytes)
        return keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, file.iv, file.ciphertext).decodeToString()
    }

    @Test
    fun `a missing file is not found, not an error`() {
        assertIs<DecryptNostrCredentialsResult.Failure.FileNotFound>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    @Test
    fun `round trips a secret and a public entry by their public keys`() {
        val credentials = mapOf(secret(first), public(second))

        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials.encrypt(credentials))
        val result = NostrCredentialManager.loadAndDecrypt(dir)

        assertIs<DecryptNostrCredentialsResult.Success>(result)
        assertEquals(credentials, result.credentials)
        assertEquals(64, result.credentials.keys.first().length, "keyed by the x-only public key, hex")
    }

    /**
     * The JSON is the contract an older or newer build reads; pin its shape rather than
     * only that it round-trips through the same code.
     */
    @Test
    fun `the entries are typed by a discriminator, and a public entry carries nothing else`() {
        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials.encrypt(mapOf(secret(first), public(second))))

        val json = readJson()

        assertTrue(json.contains(""""${first.nostrPublicKeyHex()}":{"type":"secret","privateKey":"${first.value.toHex()}"}"""), json)
        assertTrue(json.contains(""""${second.nostrPublicKeyHex()}":{"type":"public"}"""), json)
    }

    @Test
    fun `a public entry becomes a secret one in a single write, under the same key`() {
        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials.encrypt(mapOf(public(first), secret(second))))
        val before = (NostrCredentialManager.loadAndDecrypt(dir) as DecryptNostrCredentialsResult.Success).credentials

        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials.encrypt(before + secret(first)))
        val after = (NostrCredentialManager.loadAndDecrypt(dir) as DecryptNostrCredentialsResult.Success).credentials

        assertEquals(setOf(first.nostrPublicKeyHex(), second.nostrPublicKeyHex()), after.keys)
        assertEquals(NostrCredential.Secret(first), after[first.nostrPublicKeyHex()])
        assertFalse(FileSystem.SYSTEM.exists(dir.resolve("temporary_nostr_credentials.dat")))
    }

    @Test
    fun `a secret filed under the wrong public key is refused as corrupt`() {
        writeRaw("""{"${second.nostrPublicKeyHex()}":{"type":"secret","privateKey":"${first.value.toHex()}"}}""")

        assertIs<DecryptNostrCredentialsResult.Failure.SerializationError>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    @Test
    fun `a public entry filed under something that is not an x-only key is refused as corrupt`() {
        writeRaw("""{"not a key":{"type":"public"}}""")
        assertIs<DecryptNostrCredentialsResult.Failure.SerializationError>(NostrCredentialManager.loadAndDecrypt(dir))

        // Sixty-four hex characters that do not name a point on the curve: the field prime
        // itself is not a valid x coordinate.
        writeRaw("""{"fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f":{"type":"public"}}""")
        assertIs<DecryptNostrCredentialsResult.Failure.SerializationError>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    @Test
    fun `an entry of an unknown type is a serialization error`() {
        writeRaw("""{"${first.nostrPublicKeyHex()}":{"type":"remote","url":"wss://example"}}""")

        assertIs<DecryptNostrCredentialsResult.Failure.SerializationError>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    @Test
    fun `a file of another version is a serialization error, not unreadable`() {
        File(dataDir, "nostr-credentials.dat").writeBytes(byteArrayOf(9) + ByteArray(16) + ByteArray(32))

        assertIs<DecryptNostrCredentialsResult.Failure.SerializationError>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    @Test
    fun `an empty file is unreadable`() {
        File(dataDir, "nostr-credentials.dat").writeBytes(ByteArray(0))

        assertIs<DecryptNostrCredentialsResult.Failure.FileUnreadable>(NostrCredentialManager.loadAndDecrypt(dir))
    }

    // --- migration from the version-1 file ---

    private fun writeV1(vararg keys: PrivateKey) =
        File(dataDir, "nostr-keys.dat").writeBytes(EncryptedNostrKeys.encrypt(keys.associateBy { it.nostrPublicKeyHex() }).serialize())

    @Test
    fun `the version-1 file is read once, written as secrets, and deleted`() {
        writeV1(first, third)

        val outcome = NostrCredentialManager.migrateFromNostrKeys(dir)

        assertEquals(NostrCredentialManager.MigrationResult.Migrated(2), outcome)
        assertFalse(FileSystem.SYSTEM.exists(dir.resolve("nostr-keys.dat")), "the old file must not survive to be read by an older build")
        val result = NostrCredentialManager.loadAndDecrypt(dir)
        assertIs<DecryptNostrCredentialsResult.Success>(result)
        assertEquals(mapOf(secret(first), secret(third)), result.credentials)
    }

    @Test
    fun `with nothing to migrate, nothing happens`() {
        assertEquals(NostrCredentialManager.MigrationResult.NotNeeded, NostrCredentialManager.migrateFromNostrKeys(dir))
        assertFalse(FileSystem.SYSTEM.exists(dir.resolve("nostr-credentials.dat")))
    }

    @Test
    fun `once the new file exists, an old file beside it is ignored rather than merged`() {
        NostrCredentialManager.writeToDir(dir, EncryptedNostrCredentials.encrypt(mapOf(public(second))))
        writeV1(first)

        assertEquals(NostrCredentialManager.MigrationResult.NotNeeded, NostrCredentialManager.migrateFromNostrKeys(dir))
        val result = NostrCredentialManager.loadAndDecrypt(dir)
        assertIs<DecryptNostrCredentialsResult.Success>(result)
        assertEquals(mapOf(public(second)), result.credentials)
        assertTrue(FileSystem.SYSTEM.exists(dir.resolve("nostr-keys.dat")), "not this function's to delete")
    }

    @Test
    fun `an old file that cannot be read is left where it is, and the failure is reported`() {
        File(dataDir, "nostr-keys.dat").writeBytes(byteArrayOf(9) + ByteArray(16) + ByteArray(32))

        val outcome = NostrCredentialManager.migrateFromNostrKeys(dir)

        assertIs<NostrCredentialManager.MigrationResult.Failed>(outcome)
        assertTrue(FileSystem.SYSTEM.exists(dir.resolve("nostr-keys.dat")))
        assertFalse(FileSystem.SYSTEM.exists(dir.resolve("nostr-credentials.dat")))
    }
}
