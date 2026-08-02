package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.security.EncryptedPinLock
import fr.acinq.phoenix.security.EncryptedPinSpending
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

    private fun getDataDir(): Path {
        val datadir = SeedManager.getDatadir()
        if (!FileSystem.SYSTEM.exists(datadir)) {
            FileSystem.SYSTEM.createDirectory(datadir)
        }
        return datadir
    }

    private fun getEncryptedPinFromDisk(fileName: String): ByteArray? {
        val encryptedPinFile = getDataDir().resolve(fileName)
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

    fun getLockPinMapFromDisk(): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk( LOCK_PIN_FILE_NAME)?.let {
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

    fun getSpendingPinMapFromDisk(): Map<WalletId, String> {
        val encryptedPin = getEncryptedPinFromDisk(SPENDING_PIN_FILE_NAME)?.let {
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

    fun writeLockPinMapToDisk(pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinLock.encrypt(pinMap)
        val datadir = getDataDir()
        val temp = datadir.resolve("temporary_pin.dat")

        FileSystem.SYSTEM.write(temp) {
            write(encryptedPin.serialize(EncryptedPinLock.MULTIPLE_WALLET_VERSION.toInt()))
        }

        FileSystem.SYSTEM.copy(
            source = temp,
            target = datadir.resolve(LOCK_PIN_FILE_NAME.toPath())
        )
        FileSystem.SYSTEM.delete(temp)
    }

    fun writeSpendingPinMapToDisk(pinMap: Map<WalletId, String>) {
        val encryptedPin = EncryptedPinSpending.encrypt(pinMap)
        val datadir = getDataDir()

        val temp = datadir.resolve("temporary_pin.dat")
        FileSystem.SYSTEM.write(temp) {
            write(encryptedPin.serialize(EncryptedPinSpending.MULTIPLE_WALLET_VERSION.toInt()))
        }

        FileSystem.SYSTEM.copy(
            source = temp,
            target = datadir.resolve(SPENDING_PIN_FILE_NAME.toPath())
        )
        FileSystem.SYSTEM.delete(temp)
    }

    fun migrateSingleWalletPinCode(walletId: WalletId) {
        val encryptedLockPin = getEncryptedPinFromDisk(LOCK_PIN_FILE_NAME)?.let {
            EncryptedPinLock.deserialize(it)
        }
        when (encryptedLockPin) {
            is EncryptedPinLock.SingleWallet -> {
                val oldPin = encryptedLockPin.decrypt().decodeToString()
                writeLockPinMapToDisk(mapOf(walletId to oldPin))
                log.i("migrated lock-pin for wallet=$walletId")
            }
            else -> Unit
        }

        val encryptedSpendingPin = getEncryptedPinFromDisk( SPENDING_PIN_FILE_NAME)?.let {
            EncryptedPinSpending.deserialize(it)
        }
        when (encryptedSpendingPin) {
            is EncryptedPinSpending.SingleWallet -> {
                val oldPin = encryptedSpendingPin.decrypt().decodeToString()
                writeSpendingPinMapToDisk(mapOf(walletId to oldPin))
                log.i("migrated spending-pin for wallet=$walletId")
            }
            else -> Unit
        }
    }
}