package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.data.DecryptNostrKeysResult
import fr.acinq.phoenix.security.EncryptedNostrKeys
import fr.acinq.phoenix.utils.extensions.DecryptionFailure
import fr.acinq.phoenix.utils.extensions.classifyDecryptionFailure
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Reads `nostr-keys.dat`, the version-1 file that held bare nostr secrets before
 * `nostr-credentials.dat` replaced it. Read-only: its one caller is
 * [NostrCredentialManager.migrateFromNostrKeys], which converts the file once and
 * deletes it. Nothing writes this file any more, and nothing should.
 *
 * This was `NostrKeyManager`. The read half is kept intact so that the migration reads
 * the old file exactly as the build that wrote it did; the write half is gone.
 */
object LegacyNostrKeysFile {
    internal const val KEYS_FILE = "nostr-keys.dat"
    private val log = Logger.withTag("LegacyNostrKeysFile")

    fun exists(dir: Path): Boolean = FileSystem.SYSTEM.exists(dir.resolve(KEYS_FILE))

    /**
     * Reads [dir]/`nostr-keys.dat`.
     *
     * Three failures are told apart because they want three different reactions. A file
     * that cannot be read at all is [DecryptNostrKeysResult.Failure.FileUnreadable]; one
     * that reads but is not this file's shape -- wrong version byte, JSON that does not
     * parse, an entry whose key does not derive the public key it is filed under -- is
     * [DecryptNostrKeysResult.Failure.SerializationError]; and the key store refusing to
     * serve its key is [DecryptNostrKeysResult.Failure.KeyStoreFailure], which says
     * nothing about the file. [classifyDecryptionFailure] draws those lines, the same
     * way for every encrypted file here.
     */
    fun loadAndDecrypt(dir: Path): DecryptNostrKeysResult {
        log.i("loadAndDecrypt")
        val serialized = try {
            readFile(dir.resolve(KEYS_FILE)) ?: return DecryptNostrKeysResult.Failure.FileNotFound
        } catch (e: Exception) {
            log.e("couldn't read nostr keys file: ", e)
            return DecryptNostrKeysResult.Failure.FileUnreadable
        }

        return try {
            DecryptNostrKeysResult.Success(EncryptedNostrKeys.deserialize(serialized).decryptAndGetKeyMap())
        } catch (e: Exception) {
            when (classifyDecryptionFailure(e)) {
                DecryptionFailure.Serialization -> {
                    log.e("failed to decrypt nostr keys: ${e::class.simpleName}")
                    DecryptNostrKeysResult.Failure.SerializationError
                }
                DecryptionFailure.KeyStore -> {
                    log.e("failed to decrypt nostr keys: ", e)
                    DecryptNostrKeysResult.Failure.KeyStoreFailure(e)
                }
                DecryptionFailure.Other -> {
                    log.e("failed to decrypt nostr keys: ", e)
                    DecryptNostrKeysResult.Failure.DecryptionError(e)
                }
            }
        }
    }

    /** Deletes the file once its contents live in `nostr-credentials.dat`. */
    fun delete(dir: Path) {
        FileSystem.SYSTEM.delete(dir.resolve(KEYS_FILE), mustExist = false)
    }

    /** Null when the file does not exist; throws when it exists and cannot be read. */
    private fun readFile(file: Path): ByteArray? {
        if (!FileSystem.SYSTEM.exists(file)) {
            log.i("nostr keys file doesn't exist")
            return null
        }
        val metadata = FileSystem.SYSTEM.metadataOrNull(file) ?: throw UnreadableKeys("file is unreadable")
        if (metadata.isRegularFile != true) throw UnreadableKeys("not a file")
        return FileSystem.SYSTEM.source(file).buffer().use { source ->
            source.readByteArray().also {
                if (it.isEmpty()) throw UnreadableKeys("empty file!")
            }
        }
    }

    class UnreadableKeys(msg: String) : RuntimeException(msg)
}

/** The keys a version-1 file holds, as the credentials they become. */
internal fun Map<String, PrivateKey>.toCredentials(): Map<String, fr.acinq.phoenix.security.NostrCredential> =
    mapValues { (_, privateKey) -> fr.acinq.phoenix.security.NostrCredential.Secret(privateKey) }
