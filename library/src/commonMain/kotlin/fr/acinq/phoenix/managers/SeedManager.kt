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
import fr.acinq.phoenix.utils.extensions.gracefulMultiSeedDecryption
import fr.acinq.phoenix.utils.extensions.gracefulSingleSeedDecryption
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

object SeedManager {
    private val BASE_DATADIR = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve( "node-data")
    private val SEED_FILE = "seed.dat"
    private val log = Logger.withTag("SeedManager")

    init {
        log.i("Canonical: $BASE_DATADIR")
    }
    fun getDatadir(): Path {

        if (!FileSystem.SYSTEM.exists(BASE_DATADIR)) {
            log.i("Base directory doesn't exist: $BASE_DATADIR")
            FileSystem.SYSTEM.createDirectory(BASE_DATADIR)
        }

        log.i("Base directory: $BASE_DATADIR")
        return BASE_DATADIR
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

                    PinManager.migrateSingleWalletPinCode(walletId)

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
    fun loadEncryptedSeedFromDisk(phoenixGlobal: PhoenixGlobal): EncryptedSeed? = loadSeedFromDir(getDatadir(), SEED_FILE)

    /** Extracts an encrypted seed contained in a given file/folder. Returns null if the file does not exist. */
    private fun loadSeedFromDir(dir: Path, seedFileName: String): EncryptedSeed? {
//        val seedFile = File(dir, seedFileName)
        val seedFile = dir.resolve(seedFileName)
        val seedFileMetadata = FileSystem.SYSTEM.metadataOrNull(seedFile)
        log.i("Seed file metadata: $seedFileMetadata")

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

    fun writeSeedToDisk(phoenixGlobal: PhoenixGlobal, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean = false) = writeSeedToDir(getDatadir(), seed, overwrite)

    private fun writeSeedToDir(dir: Path, seed: EncryptedSeed.V2.MultipleSeed, overwrite: Boolean) {
        // 1 - create dir
        if (!FileSystem.SYSTEM.exists(dir)) {
            FileSystem.SYSTEM.createDirectories(dir)
        }

        // 2 - encrypt and write in a temporary file
        val temp = dir.resolve("temporary_seed.dat".toPath())

        FileSystem.SYSTEM.write(temp) {
            write(seed.serialize())
        }

        // 3 - decrypt temp file and check validity; if correct, move temp file to final file
        val checkSeed = loadSeedFromDir(dir, temp.name) as EncryptedSeed.V2.MultipleSeed
        if (!checkSeed.ciphertext.contentEquals(seed.ciphertext)) {
            log.w("seed check do not match!")
//            throw WriteErrorCheckDontMatch
        }
        FileSystem.SYSTEM.copy(
            source = temp,
            target = dir.resolve(SEED_FILE.toPath())
        )
        FileSystem.SYSTEM.delete(temp)
    }

    class WriteErrorCheckDontMatch : RuntimeException("failed to write the seed to disk: temporary file do not match")
    class UnreadableSeed(msg: String) : RuntimeException(msg)
}