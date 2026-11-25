package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.DecryptRecoveryCodesResult
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.security.EncryptedRecoveryCodes
import fr.acinq.phoenix.utils.AtomicFileWrite
import fr.acinq.phoenix.utils.extensions.DecryptionFailure
import fr.acinq.phoenix.utils.extensions.classifyDecryptionFailure
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Reads and writes `recovery-codes.dat`: the recovery code behind each profile that has one.
 *
 * Beside `seed.dat` in [SeedManager.getDatadir], under the same keystore key, and the shape
 * of [NostrCredentialManager] with the type changed. A missing file is an empty map, not an
 * error: a device with no codes has never written one.
 */
object RecoveryCodeManager {
    private const val CODES_FILE = "recovery-codes.dat"
    private const val TEMPORARY_CODES_FILE = "temporary_recovery_codes.dat"
    private val log = Logger.withTag("RecoveryCodeManager")

    fun loadAndDecrypt(phoenixGlobal: PhoenixGlobal): DecryptRecoveryCodesResult =
        loadAndDecrypt(SeedManager.getDatadir(phoenixGlobal.ctx))

    /**
     * Wrapper for [loadAndDecrypt].
     * Returns an empty map if the file does not exist yet.
     * Returns null if there was a problem loading or decrypting it.
     */
    fun loadAndDecryptOrNull(phoenixGlobal: PhoenixGlobal): Map<WalletId, String>? =
        orNull(loadAndDecrypt(phoenixGlobal))

    fun writeToDisk(phoenixGlobal: PhoenixGlobal, codes: EncryptedRecoveryCodes) =
        writeToDir(SeedManager.getDatadir(phoenixGlobal.ctx), codes)

    internal fun orNull(result: DecryptRecoveryCodesResult): Map<WalletId, String>? = when (result) {
        is DecryptRecoveryCodesResult.Success -> result.codes
        is DecryptRecoveryCodesResult.Failure.FileNotFound -> emptyMap()
        is DecryptRecoveryCodesResult.Failure -> null
    }

    internal fun loadAndDecrypt(dir: Path): DecryptRecoveryCodesResult {
        val serialized = try {
            readFile(dir.resolve(CODES_FILE)) ?: return DecryptRecoveryCodesResult.Failure.FileNotFound
        } catch (e: Exception) {
            log.e("couldn't read recovery codes file: ", e)
            return DecryptRecoveryCodesResult.Failure.FileUnreadable
        }

        return try {
            DecryptRecoveryCodesResult.Success(EncryptedRecoveryCodes.deserialize(serialized).decryptAndGetCodes())
        } catch (e: Exception) {
            when (classifyDecryptionFailure(e)) {
                DecryptionFailure.Serialization -> {
                    log.e("failed to decrypt recovery codes: ${e::class.simpleName}")
                    DecryptRecoveryCodesResult.Failure.SerializationError
                }
                DecryptionFailure.KeyStore -> {
                    log.e("failed to decrypt recovery codes: ", e)
                    DecryptRecoveryCodesResult.Failure.KeyStoreFailure(e)
                }
                DecryptionFailure.Other -> {
                    log.e("failed to decrypt recovery codes: ", e)
                    DecryptRecoveryCodesResult.Failure.DecryptionError(e)
                }
            }
        }
    }

    /** Null when the file does not exist; throws when it exists and cannot be read. */
    private fun readFile(file: Path): ByteArray? {
        if (!FileSystem.SYSTEM.exists(file)) return null
        val metadata = FileSystem.SYSTEM.metadataOrNull(file) ?: throw UnreadableCodes("file is unreadable")
        if (metadata.isRegularFile != true) throw UnreadableCodes("not a file")
        return FileSystem.SYSTEM.source(file).buffer().use { source ->
            source.readByteArray().also {
                if (it.isEmpty()) throw UnreadableCodes("empty file!")
            }
        }
    }

    internal fun writeToDir(dir: Path, codes: EncryptedRecoveryCodes) {
        val bytes = codes.serialize()
        AtomicFileWrite.writeVerified(
            dir = dir,
            fileName = CODES_FILE,
            temporaryFileName = TEMPORARY_CODES_FILE,
            bytes = bytes,
            check = { readBack -> readBack.contentEquals(bytes) },
            onMismatch = { WriteErrorCheckDontMatch() },
        )
    }

    class WriteErrorCheckDontMatch : RuntimeException("failed to write the recovery codes to disk: temporary file does not match")
    class UnreadableCodes(msg: String) : RuntimeException(msg)
}
