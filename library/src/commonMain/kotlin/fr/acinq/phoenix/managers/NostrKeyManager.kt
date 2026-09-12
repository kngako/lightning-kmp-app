package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.DecryptNostrKeysResult
import fr.acinq.phoenix.security.EncryptedNostrKeys
import fr.acinq.phoenix.utils.AtomicFileWrite
import fr.acinq.phoenix.utils.extensions.DecryptionFailure
import fr.acinq.phoenix.utils.extensions.classifyDecryptionFailure
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Reads and writes `nostr-keys.dat`: the bare nostr secrets this device holds that did
 * not come from a seed.
 *
 * Lives next to `seed.dat` in [SeedManager.getDatadir] -- the durable, app-private
 * location whose requirements that function spells out -- and is encrypted under the
 * same keystore key. It is the shape of [SeedManager] with the type changed, and it
 * shares the atomic write with it through [AtomicFileWrite].
 *
 * An older build of the consuming app does not know this file exists. It reads
 * `seed.dat` as it always did, so a downgrade loses sight of an imported key without
 * touching any wallet; the file is still there for the next upgrade.
 */
object NostrKeyManager {
    private const val KEYS_FILE = "nostr-keys.dat"
    private const val TEMPORARY_KEYS_FILE = "temporary_nostr_keys.dat"
    private val log = Logger.withTag("NostrKeyManager")

    fun loadAndDecrypt(phoenixGlobal: PhoenixGlobal): DecryptNostrKeysResult =
        loadAndDecrypt(SeedManager.getDatadir(phoenixGlobal.ctx))

    /**
     * Wrapper for [loadAndDecrypt].
     * Returns an empty map if the file does not exist yet.
     * Returns null if there was a problem loading or decrypting it.
     */
    fun loadAndDecryptOrNull(phoenixGlobal: PhoenixGlobal): Map<String, PrivateKey>? =
        when (val res = loadAndDecrypt(phoenixGlobal)) {
            is DecryptNostrKeysResult.Success -> res.keys
            is DecryptNostrKeysResult.Failure.FileNotFound -> emptyMap()
            is DecryptNostrKeysResult.Failure -> null
        }

    fun writeToDisk(phoenixGlobal: PhoenixGlobal, keys: EncryptedNostrKeys) =
        writeToDir(SeedManager.getDatadir(phoenixGlobal.ctx), keys)

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
    internal fun loadAndDecrypt(dir: Path): DecryptNostrKeysResult {
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

    internal fun writeToDir(dir: Path, keys: EncryptedNostrKeys) {
        val bytes = keys.serialize()
        AtomicFileWrite.writeVerified(
            dir = dir,
            fileName = KEYS_FILE,
            temporaryFileName = TEMPORARY_KEYS_FILE,
            bytes = bytes,
            check = { readBack -> readBack.contentEquals(bytes) },
            onMismatch = { WriteErrorCheckDontMatch() },
        )
    }

    class WriteErrorCheckDontMatch : RuntimeException("failed to write the nostr keys to disk: temporary file does not match")
    class UnreadableKeys(msg: String) : RuntimeException(msg)
}
