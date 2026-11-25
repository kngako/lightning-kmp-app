package fr.acinq.phoenix.managers

import fr.acinq.phoenix.data.DecryptRecoveryCodesResult
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.security.EncryptedRecoveryCodes
import fr.acinq.phoenix.security.JvmKeyStore
import fr.acinq.phoenix.security.KeyStoreNames
import fr.acinq.phoenix.security.keyStoreDecryption
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
 * Encrypt, write, read back, decrypt -- on the jvm, the one target where the key store can
 * be unlocked on the host.
 */
class RecoveryCodeManagerJvmTest {

    private lateinit var storeDir: File
    private lateinit var dataDir: File

    private val first = WalletId("11".repeat(20)) to "LBZC-MYTK-E9P3-7C7B-AHJN-KX79-22CY-WAY7-K7QZ"
    private val second = WalletId("22".repeat(20)) to "LB33-MXW2-RPRD-ZREJ-QRHW-RBW9-982X-HLSC-Z2U8"

    @BeforeTest
    fun setUp() {
        storeDir = Files.createTempDirectory("phoenix-recovery-codes-store").toFile()
        dataDir = Files.createTempDirectory("phoenix-recovery-codes-data").toFile()
        JvmKeyStore.lock()
        JvmKeyStore.unlock("correct horse battery staple".toCharArray(), storeDir)
    }

    @AfterTest
    fun tearDown() {
        JvmKeyStore.lock()
        storeDir.deleteRecursively()
        dataDir.deleteRecursively()
    }

    private val dir get() = dataDir.toOkioPath()

    private fun writeRaw(json: String) {
        val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, json.encodeToByteArray())
        RecoveryCodeManager.writeToDir(dir, EncryptedRecoveryCodes(iv, ciphertext))
    }

    @Test
    fun `a missing file is not found, and reads as no codes`() {
        val result = RecoveryCodeManager.loadAndDecrypt(dir)
        assertIs<DecryptRecoveryCodesResult.Failure.FileNotFound>(result)
        assertEquals(emptyMap(), RecoveryCodeManager.orNull(result))
    }

    @Test
    fun `round trips codes by profile id`() {
        RecoveryCodeManager.writeToDir(dir, EncryptedRecoveryCodes.encrypt(mapOf(first, second)))
        val result = assertIs<DecryptRecoveryCodesResult.Success>(RecoveryCodeManager.loadAndDecrypt(dir))
        assertEquals(mapOf(first, second), result.codes)
    }

    @Test
    fun `the plaintext is a map of id to code`() {
        RecoveryCodeManager.writeToDir(dir, EncryptedRecoveryCodes.encrypt(mapOf(first)))
        val file = EncryptedRecoveryCodes.deserialize(File(dataDir, "recovery-codes.dat").readBytes())
        val json = keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, file.iv, file.ciphertext).decodeToString()
        assertEquals("""{"${"11".repeat(20)}":"${first.second}"}""", json)
    }

    @Test
    fun `a second write replaces the first`() {
        RecoveryCodeManager.writeToDir(dir, EncryptedRecoveryCodes.encrypt(mapOf(first)))
        RecoveryCodeManager.writeToDir(dir, EncryptedRecoveryCodes.encrypt(mapOf(first, second)))
        val result = assertIs<DecryptRecoveryCodesResult.Success>(RecoveryCodeManager.loadAndDecrypt(dir))
        assertEquals(2, result.codes.size)
    }

    @Test
    fun `a code filed under something that is not a profile id is refused as corrupt`() {
        writeRaw("""{"not-an-id":"${first.second}"}""")
        val result = RecoveryCodeManager.loadAndDecrypt(dir)
        assertIs<DecryptRecoveryCodesResult.Failure.SerializationError>(result)
        assertEquals(null, RecoveryCodeManager.orNull(result))
    }

    @Test
    fun `an empty file is unreadable`() {
        File(dataDir, "recovery-codes.dat").writeBytes(ByteArray(0))
        assertIs<DecryptRecoveryCodesResult.Failure.FileUnreadable>(RecoveryCodeManager.loadAndDecrypt(dir))
    }
}
