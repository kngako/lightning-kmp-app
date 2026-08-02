package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.security.EncryptedPinLock
import fr.acinq.phoenix.security.EncryptedPinSpending
import fr.acinq.phoenix.utils.PlatformContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

object PinManager {
    val log = Logger.withTag("PinManager")

    private const val LOCK_PIN_FILE_NAME = "pin.dat"
    private const val SPENDING_PIN_FILE_NAME = "spending_pin.dat"

    /** The pin codes live alongside the seed, in the durable app-private directory. See [SeedManager.getDatadir]. */
    private fun getDataDir(ctx: PlatformContext): Path = SeedManager.getDatadir(ctx)

    private fun getEncryptedPinFromDisk(ctx: PlatformContext, fileName: String): ByteArray? {
        val encryptedPinFile = getDataDir(ctx).resolve(fileName)
        val encryptedPinFileMetadata = FileSystem.SYSTEM.metadataOrNull(encryptedPinFile)

        return if (!FileSystem.SYSTEM.exists(encryptedPinFile)) {
            null
        } else if (encryptedPinFileMetadata == null) {
            log.w("$fileName is unreadable")
            null
        } else if (!encryptedPinFileMetadata.isRegularFile) {
            log.w("$fileName  is not a file")
            null
            // TODO: Check if file is writable
//        } else if (!encryptedPinFile.isFile || !encryptedPinFile.canRead() || !encryptedPinFile.canWrite()) {
//            log.warn("$fileName exists but is not usable")
//            null
        } else {
            FileSystem.SYSTEM.source(encryptedPinFile).buffer().use { source ->
                source.readByteArray().let {
                    if (it.isEmpty()) {
                        null
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun getLockPinMapFromDisk(ctx: PlatformContext): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk(ctx, LOCK_PIN_FILE_NAME)?.let {
            EncryptedPinLock.deserialize(it)
        }
        return when (encryptedPin) {
            is EncryptedPinLock.SingleWallet -> {
                log.w("[SingleWallet] lock-pin, migration should be performed")
                emptyMap()
            }
            is EncryptedPinLock.MultipleWallet -> encryptedPin.decryptAndGetPins()
            null -> emptyMap()
        }
    }

    fun getSpendingPinMapFromDisk(ctx: PlatformContext): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk(ctx, SPENDING_PIN_FILE_NAME)?.let {
            EncryptedPinSpending.deserialize(it)
        }
        return when (encryptedPin) {
            is EncryptedPinSpending.SingleWallet -> {
                log.w("[SingleWallet] spending-pin, migration should be performed")
                emptyMap()
            }
            is EncryptedPinSpending.MultipleWallet -> encryptedPin.decryptAndGetPins()
            null -> emptyMap()
        }
    }

    fun writeLockPinMapToDisk(ctx: PlatformContext, pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinLock.encrypt(pinMap)
        val datadir = getDataDir(ctx)
        val temp = datadir.resolve("temporary_lock_pin.dat")

        FileSystem.SYSTEM.write(temp) {
            write(encryptedPin.serialize(EncryptedPinLock.MULTIPLE_WALLET_VERSION.toInt()))
        }

        FileSystem.SYSTEM.atomicMove(
            source = temp,
            target = datadir.resolve(LOCK_PIN_FILE_NAME.toPath())
        )
    }

    fun writeSpendingPinMapToDisk(ctx: PlatformContext, pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinSpending.encrypt(pinMap)
        val datadir = getDataDir(ctx)

        val temp = datadir.resolve("temporary_spending_pin.dat")
        FileSystem.SYSTEM.write(temp) {
            write(encryptedPin.serialize(EncryptedPinSpending.MULTIPLE_WALLET_VERSION.toInt()))
        }

        FileSystem.SYSTEM.atomicMove(
            source = temp,
            target = datadir.resolve(SPENDING_PIN_FILE_NAME.toPath())
        )
    }

    fun migrateSingleWalletPinCode(ctx: PlatformContext, walletId: WalletId) {
        val encryptedLockPin = getEncryptedPinFromDisk(ctx, LOCK_PIN_FILE_NAME)?.let {
            EncryptedPinLock.deserialize(it)
        }
        when (encryptedLockPin) {
            is EncryptedPinLock.SingleWallet -> {
                val oldPin = encryptedLockPin.decrypt().decodeToString()
                writeLockPinMapToDisk(ctx, mapOf(walletId to oldPin))
                log.i("migrated lock-pin for wallet=$walletId")
            }
            else -> Unit
        }

        val encryptedSpendingPin = getEncryptedPinFromDisk(ctx, SPENDING_PIN_FILE_NAME)?.let {
            EncryptedPinSpending.deserialize(it)
        }
        when (encryptedSpendingPin) {
            is EncryptedPinSpending.SingleWallet -> {
                val oldPin = encryptedSpendingPin.decrypt().decodeToString()
                writeSpendingPinMapToDisk(ctx, mapOf(walletId to oldPin))
                log.i("migrated spending-pin for wallet=$walletId")
            }
            else -> Unit
        }
    }
}