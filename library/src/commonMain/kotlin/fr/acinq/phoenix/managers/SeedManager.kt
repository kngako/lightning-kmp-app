package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.DecryptSeedResult
import fr.acinq.phoenix.data.UserWallet
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.managers.SeedManager.loadAndDecrypt
import fr.acinq.phoenix.security.EncryptedSeed
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.extensions.gracefulMultiSeedDecryption
import fr.acinq.phoenix.utils.extensions.gracefulSingleSeedDecryption
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

object SeedManager {
    private val SEED_FILE = "seed.dat"
    private val log = Logger.withTag("SeedManager")

    /**
     * Returns the directory holding the encrypted seed (and the encrypted pin codes, see [PinManager]).
     *
     * This MUST be a durable, app-private location: on Android that's `Context.filesDir`, on iOS the
     * app's Documents directory. In particular it must never be a cache or temporary directory, as those
     * are purged by the OS under storage pressure (and by "clear cache" on Android), which would destroy
     * the only copy of the user's seed.
     */
    fun getDatadir(ctx: PlatformContext): Path {
        val datadir = getApplicationFilesDirectoryPath(ctx).toPath().resolve("node-data")

        if (!FileSystem.SYSTEM.exists(datadir)) {
            log.i("base directory doesn't exist, creating it")
            FileSystem.SYSTEM.createDirectories(datadir)
        }

        return datadir
    }

    @Suppress("DEPRECATION")
    fun loadAndDecrypt(phoenixGlobal: PhoenixGlobal): DecryptSeedResult {
        log.i("loadAndDecrypt")
        val encryptedSeed = try {
            loadEncryptedSeedFromDisk(phoenixGlobal)
        } catch (e: Exception) {
            log.e("couldn't read seed file: ", e)
            return DecryptSeedResult.Failure.SeedFileUnreadable
        }

        return when (encryptedSeed) {
            is EncryptedSeed.V2.SingleSeed -> {
                log.i("decrypting [V2.SingleSeed]...")

                gracefulSingleSeedDecryption {
                    val payload = encryptedSeed.decrypt()

                    val words = EncryptedSeed.V2.SingleSeed.toMnemonicsSafe(payload) ?: return DecryptSeedResult.Failure.SeedInvalid

                    val seed = MnemonicCode.toSeed(words, "").toByteVector()
                    val keyManager = LocalKeyManager(
                        seed,
                        NodeParamsManager.chain,
                        NodeParamsManager.remoteSwapInXpub
                    )
                    val nodeId = keyManager.nodeKeys.nodeKey.publicKey
                    val walletId = WalletId(nodeId)

                    PinManager.migrateSingleWalletPinCode(phoenixGlobal.ctx, walletId)

                    DecryptSeedResult.Success(
                        userWalletsMap = mapOf(
                            walletId to UserWallet(
                                walletId,
                                nodeId.toHex(),
                                words
                            )
                        )
                    )
                }
            }

            is EncryptedSeed.V2.MultipleSeed -> {
                log.i("decrypting [V2.MultipleSeed]")
                gracefulMultiSeedDecryption {
                    val seedMap = encryptedSeed.decryptAndGetSeedMap()

                    when {
                        seedMap.isEmpty() -> DecryptSeedResult.Failure.SeedFileNotFound
                        else -> {
                            seedMap.map { (walletId, words) ->
                                val seed = MnemonicCode.toSeed(words, "").toByteVector()
                                val keyManager = LocalKeyManager(
                                    seed,
                                    NodeParamsManager.chain,
                                    NodeParamsManager.remoteSwapInXpub
                                )
                                val nodeId = keyManager.nodeKeys.nodeKey.publicKey
                                walletId to UserWallet(walletId, nodeId.toHex(), words)
                            }.toMap().let {
                                DecryptSeedResult.Success(it)
                            }
                        }
                    }
                }
            }

            null -> DecryptSeedResult.Failure.SeedFileNotFound
        }
    }

    /**
     * Wrapper method for [loadAndDecrypt].
     * Returns an empty map if the seed file does not exist yet.
     * Returns null if there was a problem when loading or decrypting the seed file.
     */
    fun loadAndDecryptOrNull(phoenixGlobal: PhoenixGlobal): Map<WalletId, UserWallet>? = when (val res = loadAndDecrypt(phoenixGlobal)) {
        is DecryptSeedResult.Success -> res.userWalletsMap
        is DecryptSeedResult.Failure.SeedFileNotFound -> emptyMap()
        is DecryptSeedResult.Failure -> null
    }

    /** Gets the encrypted seed from app private dir. */
    fun loadEncryptedSeedFromDisk(phoenixGlobal: PhoenixGlobal): EncryptedSeed? = loadSeedFromDir(getDatadir(phoenixGlobal.ctx), SEED_FILE)

    /** Extracts an encrypted seed contained in a given file/folder. Returns null if the file does not exist. */
    private fun loadSeedFromDir(dir: Path, seedFileName: String): EncryptedSeed? {
        val seedFile = dir.resolve(seedFileName)
        val seedFileMetadata = FileSystem.SYSTEM.metadataOrNull(seedFile)

        return if (!FileSystem.SYSTEM.exists(seedFile)) {
            log.i("seed file doesn't exist")
            null
        } else if (seedFileMetadata == null) {
            throw UnreadableSeed("file is unreadable")
        } else if (seedFileMetadata.isRegularFile != true) {
            throw UnreadableSeed("not a file")
        } else {
            FileSystem.SYSTEM.source(seedFile).buffer().use { source ->
                source.readByteArray().let {
                    if (it.isEmpty()) {
                        throw UnreadableSeed("empty file!")
                    } else {
                        EncryptedSeed.deserialize(it)
                    }
                }
            }
        }
    }

    fun writeSeedToDisk(phoenixGlobal: PhoenixGlobal, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean = false) =
        writeSeedToDir(getDatadir(phoenixGlobal.ctx), seed, overwrite)

    private fun writeSeedToDir(dir: Path, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean) {
        // 1 - create dir
        if (!FileSystem.SYSTEM.exists(dir)) {
            FileSystem.SYSTEM.createDirectories(dir)
        }

        // 2 - encrypt and write in a temporary file
        val temp = dir.resolve("temporary_seed.dat".toPath())

        try {
            FileSystem.SYSTEM.write(temp) {
                write(seed.serialize())
            }

            // 3 - read the temp file back and check that it matches what we meant to write. If it doesn't, abort
            // rather than replace a good seed file with a corrupted one.
            val checkSeed = loadSeedFromDir(dir, temp.name)
            if (checkSeed !is EncryptedSeed.V2.MultipleSeed
                || !checkSeed.iv.contentEquals(seed.iv)
                || !checkSeed.ciphertext.contentEquals(seed.ciphertext)
            ) {
                log.e("seed check does not match, aborting write")
                throw WriteErrorCheckDontMatch()
            }

            // 4 - atomically replace the seed file. A plain copy is not safe here: an interruption partway
            // through would leave a truncated seed.dat, i.e. an unrecoverable wallet.
            FileSystem.SYSTEM.atomicMove(
                source = temp,
                target = dir.resolve(SEED_FILE.toPath())
            )
        } catch (e: Exception) {
            // the move consumes the temp file, so this only has an effect when we failed before that point
            try {
                FileSystem.SYSTEM.delete(temp, mustExist = false)
            } catch (cleanupError: Exception) {
                log.w("could not clean up temporary seed file: ${cleanupError.message}")
            }
            throw e
        }
    }

    class WriteErrorCheckDontMatch : RuntimeException("failed to write the seed to disk: temporary file do not match")
    class UnreadableSeed(msg: String) : RuntimeException(msg)
}