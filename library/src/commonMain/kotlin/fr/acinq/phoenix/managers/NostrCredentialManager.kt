package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.DecryptNostrCredentialsResult
import fr.acinq.phoenix.data.DecryptNostrKeysResult
import fr.acinq.phoenix.security.EncryptedNostrCredentials
import fr.acinq.phoenix.security.NostrCredential
import fr.acinq.phoenix.utils.AtomicFileWrite
import fr.acinq.phoenix.utils.extensions.DecryptionFailure
import fr.acinq.phoenix.utils.extensions.classifyDecryptionFailure
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Reads and writes `nostr-credentials.dat`: what this device holds for each nostr
 * public key that did not come from a seed -- the secret, or only the public key.
 *
 * Lives next to `seed.dat` in [SeedManager.getDatadir] -- the durable, app-private
 * location whose requirements that function spells out -- and is encrypted under the
 * same keystore key. It is the shape of [SeedManager] with the type changed, and it
 * shares the atomic write with it through [AtomicFileWrite].
 *
 * It replaces `nostr-keys.dat`. [migrateFromNostrKeys] reads that file once, writes
 * this one, and deletes it; [LegacyNostrKeysFile] is the reader it uses. An older
 * build of the consuming app does not know this file exists: it reads `seed.dat` as it
 * always did, so a downgrade loses sight of every imported identity without touching
 * any wallet, and -- once the migration has run -- finds no `nostr-keys.dat` either.
 * That is deliberate: a frozen copy of the old file would let a key the user asked
 * this build to forget come back on a downgrade.
 */
object NostrCredentialManager {
    private const val CREDENTIALS_FILE = "nostr-credentials.dat"
    private const val TEMPORARY_CREDENTIALS_FILE = "temporary_nostr_credentials.dat"
    private val log = Logger.withTag("NostrCredentialManager")

    fun loadAndDecrypt(phoenixGlobal: PhoenixGlobal): DecryptNostrCredentialsResult =
        loadAndDecrypt(SeedManager.getDatadir(phoenixGlobal.ctx))

    /**
     * Wrapper for [loadAndDecrypt].
     * Returns an empty map if the file does not exist yet.
     * Returns null if there was a problem loading or decrypting it.
     */
    fun loadAndDecryptOrNull(phoenixGlobal: PhoenixGlobal): Map<String, NostrCredential>? =
        when (val res = loadAndDecrypt(phoenixGlobal)) {
            is DecryptNostrCredentialsResult.Success -> res.credentials
            is DecryptNostrCredentialsResult.Failure.FileNotFound -> emptyMap()
            is DecryptNostrCredentialsResult.Failure -> null
        }

    fun writeToDisk(phoenixGlobal: PhoenixGlobal, credentials: EncryptedNostrCredentials) =
        writeToDir(SeedManager.getDatadir(phoenixGlobal.ctx), credentials)

    fun migrateFromNostrKeys(phoenixGlobal: PhoenixGlobal): MigrationResult =
        migrateFromNostrKeys(SeedManager.getDatadir(phoenixGlobal.ctx))

    /** What [migrateFromNostrKeys] found. */
    sealed class MigrationResult {
        /** The old file was read, its keys written as secrets, and the old file deleted. */
        data class Migrated(val count: Int) : MigrationResult()

        /** No old file, or the new file already exists: nothing to do. */
        data object NotNeeded : MigrationResult()

        /** The old file exists and could not be read. Both files are left as they were. */
        data class Failed(val failure: DecryptNostrKeysResult.Failure) : MigrationResult()
    }

    /**
     * Converts [dir]/`nostr-keys.dat` into [dir]/`nostr-credentials.dat`, once.
     *
     * Only when the new file is absent: once it exists it is the only truth, and an old
     * file that somehow reappears beside it is ignored rather than merged, because
     * merging would be the reconciliation this file exists to make unnecessary. The
     * write is verified before the old file is deleted, so a failure partway leaves the
     * old file where it was and the caller's read reports what went wrong.
     */
    internal fun migrateFromNostrKeys(dir: Path): MigrationResult {
        if (FileSystem.SYSTEM.exists(dir.resolve(CREDENTIALS_FILE))) return MigrationResult.NotNeeded
        if (!LegacyNostrKeysFile.exists(dir)) return MigrationResult.NotNeeded

        val keys = when (val result = LegacyNostrKeysFile.loadAndDecrypt(dir)) {
            is DecryptNostrKeysResult.Success -> result.keys
            is DecryptNostrKeysResult.Failure.FileNotFound -> return MigrationResult.NotNeeded
            is DecryptNostrKeysResult.Failure -> {
                log.e("nostr-keys.dat exists and could not be read; leaving it for the next start")
                return MigrationResult.Failed(result)
            }
        }

        writeToDir(dir, EncryptedNostrCredentials.encrypt(keys.toCredentials()))
        LegacyNostrKeysFile.delete(dir)
        log.i("migrated ${keys.size} nostr key(s) into nostr-credentials.dat")
        return MigrationResult.Migrated(keys.size)
    }

    /**
     * Reads [dir]/`nostr-credentials.dat`.
     *
     * Three failures are told apart because they want three different reactions. A file
     * that cannot be read at all is [DecryptNostrCredentialsResult.Failure.FileUnreadable];
     * one that reads but is not this file's shape -- wrong version byte, JSON that does
     * not parse, an entry that fails its own check -- is
     * [DecryptNostrCredentialsResult.Failure.SerializationError]; and the key store
     * refusing to serve its key is [DecryptNostrCredentialsResult.Failure.KeyStoreFailure],
     * which says nothing about the file. [classifyDecryptionFailure] draws those lines,
     * the same way for every encrypted file here.
     */
    internal fun loadAndDecrypt(dir: Path): DecryptNostrCredentialsResult {
        log.i("loadAndDecrypt")
        val serialized = try {
            readFile(dir.resolve(CREDENTIALS_FILE)) ?: return DecryptNostrCredentialsResult.Failure.FileNotFound
        } catch (e: Exception) {
            log.e("couldn't read nostr credentials file: ", e)
            return DecryptNostrCredentialsResult.Failure.FileUnreadable
        }

        return try {
            DecryptNostrCredentialsResult.Success(
                EncryptedNostrCredentials.deserialize(serialized).decryptAndGetCredentials()
            )
        } catch (e: Exception) {
            when (classifyDecryptionFailure(e)) {
                DecryptionFailure.Serialization -> {
                    log.e("failed to decrypt nostr credentials: ${e::class.simpleName}")
                    DecryptNostrCredentialsResult.Failure.SerializationError
                }
                DecryptionFailure.KeyStore -> {
                    log.e("failed to decrypt nostr credentials: ", e)
                    DecryptNostrCredentialsResult.Failure.KeyStoreFailure(e)
                }
                DecryptionFailure.Other -> {
                    log.e("failed to decrypt nostr credentials: ", e)
                    DecryptNostrCredentialsResult.Failure.DecryptionError(e)
                }
            }
        }
    }

    /** Null when the file does not exist; throws when it exists and cannot be read. */
    private fun readFile(file: Path): ByteArray? {
        if (!FileSystem.SYSTEM.exists(file)) {
            log.i("nostr credentials file doesn't exist")
            return null
        }
        val metadata = FileSystem.SYSTEM.metadataOrNull(file) ?: throw UnreadableCredentials("file is unreadable")
        if (metadata.isRegularFile != true) throw UnreadableCredentials("not a file")
        return FileSystem.SYSTEM.source(file).buffer().use { source ->
            source.readByteArray().also {
                if (it.isEmpty()) throw UnreadableCredentials("empty file!")
            }
        }
    }

    internal fun writeToDir(dir: Path, credentials: EncryptedNostrCredentials) {
        val bytes = credentials.serialize()
        AtomicFileWrite.writeVerified(
            dir = dir,
            fileName = CREDENTIALS_FILE,
            temporaryFileName = TEMPORARY_CREDENTIALS_FILE,
            bytes = bytes,
            check = { readBack -> readBack.contentEquals(bytes) },
            onMismatch = { WriteErrorCheckDontMatch() },
        )
    }

    class WriteErrorCheckDontMatch : RuntimeException("failed to write the nostr credentials to disk: temporary file does not match")
    class UnreadableCredentials(msg: String) : RuntimeException(msg)
}
