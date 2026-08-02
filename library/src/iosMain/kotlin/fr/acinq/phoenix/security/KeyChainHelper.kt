package fr.acinq.phoenix.security

import co.touchlab.kermit.Logger
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.compose.AppVersion
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSKeyedArchiver
import platform.Foundation.NSKeyedUnarchiver
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccessibleWhenUnlocked
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import platform.darwin.noErr
import platform.posix.memcpy

/***
 * If the KVault repo was being maintained we would've probably just used that as a dependency
 *
 * https://github.com/Liftric/KVault
 */
@OptIn(ExperimentalForeignApi::class)
object KeyChainHelper {
    private val accessibility: Accessible = Accessible.AfterFirstUnlock
    /**
     * kSecAttrAccessible attributes wrapper.
     * attribute enables you to control item availability relative to the lock state of the device.
     * It also lets you specify eligibility for restoration to a new device.
     * If the attribute ends with the string ThisDeviceOnly, the item can be restored to the same device
     * that created a backup, but it isn’t migrated when restoring another device’s backup data.
     */
    enum class Accessible(val value: CFStringRef?) {
        WhenPasscodeSetThisDeviceOnly(kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly),
        WhenUnlockedThisDeviceOnly(kSecAttrAccessibleWhenUnlockedThisDeviceOnly),
        WhenUnlocked(kSecAttrAccessibleWhenUnlocked),
        AfterFirstUnlock(kSecAttrAccessibleAfterFirstUnlock),
        AfterFirstUnlockThisDeviceOnly(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
    }

    private val log = Logger.withTag("KeyChainHelper")


    /**
     * Returns the data value of an object in the store.
     * @param forKey The key to query
     * @return The stored bytes value
     */
    fun data(forKey: String): ByteArray? {
        log.i("Getting byteArray for $forKey")
        return value(forKey)?.toByteArray()
    }

    private fun value(forKey: String): NSData? = context(forKey) { (account) ->
        log.i("value for $forKey")
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to account,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        log.i("query: $query")

        memScoped {
            val result = alloc<CFTypeRefVar>()
            SecItemCopyMatching(query, result.ptr)
            CFBridgingRelease(result.value) as? NSData
        }
    }

    fun set(key: String, dataValue: ByteArray): Boolean {
        log.i("set for $key")
        return addOrUpdate(key, dataValue.toNSData())
    }

    private fun addOrUpdate(key: String, value: NSData?): Boolean {
        log.i("addOrUpdate for $key")
        return if (existsObject(key)) {
            update(key, value)
        } else {
            add(key, value)
        }
    }

    /**
     * Checks if object with the given key exists in the Keychain.
     * @param forKey The key to query
     * @return True or false, depending on whether it is in the Keychain or not
     */
    fun existsObject(forKey: String): Boolean = context(forKey) { (account) ->
        log.i("existsObject for $forKey")
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to account,
            kSecReturnData to kCFBooleanFalse,
        )
        log.i("query: $query")

        SecItemCopyMatching(query, null)
            .validate()
    }


    private fun add(key: String, value: NSData?): Boolean = context(key, value) { (account, data) ->
        log.i("add value for $key")
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to account,
            kSecValueData to data,
            kSecAttrAccessible to accessibility.value
        )
        log.i("query: $query")
        SecItemAdd(query, null)
            .validate()

    }

    private fun update(key: String, value: Any?): Boolean = context(key, value) { (account, data) ->
        log.i("update for $key")
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to account,
            kSecReturnData to kCFBooleanFalse,
        )

        val updateQuery = query(
            kSecValueData to data
        )

        SecItemUpdate(query, updateQuery)
            .validate()
    }

    private class Context(val refs: Map<CFStringRef?, CFTypeRef?>) {
        fun query(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
            val map = mapOf(*pairs).plus(refs.filter { it.value != null })
            return CFDictionaryCreateMutable(
                null, map.size.convert(), null, null
            ).apply {
                map.entries.forEach { CFDictionaryAddValue(this, it.key, it.value) }
            }.apply {
                CFAutorelease(this)
            }
        }
    }

    private fun <T> context(vararg values: Any?, block: Context.(List<CFTypeRef?>) -> T): T {
        val standard = mapOf(
            kSecAttrService to CFBridgingRetain(AppVersion.serviceName),
            kSecAttrAccessGroup to CFBridgingRetain(null)
        )
        val custom = arrayOf(*values).map { CFBridgingRetain(it) }
        return block.invoke(Context(standard), custom).apply {
            standard.values.plus(custom).forEach { CFBridgingRelease(it) }
        }
    }

    private fun String.toNSData(): NSData? =
        NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)

    private fun NSNumber.toNSData() = NSKeyedArchiver.archivedDataWithRootObject(this)
    private fun NSData.toNSNumber() = NSKeyedUnarchiver.unarchiveObjectWithData(this) as? NSNumber

    private val NSData.stringValue: String?
        get() = NSString.create(this, NSUTF8StringEncoding) as String?

    private fun NSData.toByteArray(): ByteArray =
        ByteArray(length.toInt()).apply {
            if (isNotEmpty()) {
                usePinned {
                    memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
                }
            }
        }

    private fun ByteArray.toNSData(): NSData =
        memScoped {
            NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.convert())
        }

    private fun OSStatus.validate(): Boolean {
        log.i("validate: ${toUInt()}")
        return toUInt() == noErr
    }

    private fun getOrCreateKeyNoAuthRequired(): ByteArray {
        log.i("getOrCreateKeyNoAuthRequired")
        data(KeyStoreNames.KEY_NO_AUTH)?.let {
            log.i("Key (${KeyStoreNames.KEY_NO_AUTH}) found")
            return it
        }
        log.i("Key (${KeyStoreNames.KEY_NO_AUTH}) not found need to create new key")
        // TODO: Generate secretKey using keychain
        val secretKey = Lightning.randomKey().value.toByteArray()

        if (set(KeyStoreNames.KEY_NO_AUTH, secretKey)) {
            log.i("Successfully set key")
        } else {
            log.i("failed to save key")
        }
        return secretKey
    }

    private fun getKeyForName(keyName: String): ByteArray = when (keyName) {
        KeyStoreNames.KEY_NO_AUTH -> getOrCreateKeyNoAuthRequired()
        KeyStoreNames.KEY_FOR_PINCODE_V1 -> getOrCreateKeyNoAuthRequired()
        else -> throw IllegalArgumentException("unhandled key=$keyName")
    }

    internal fun getEncryptionCipher(keyName: String): CCCipher {
        val key = getKeyForName(keyName)

        return CCCipher.phoenixCipherForKey(key)
    }

    internal fun getDecryptionCipher(keyName: String): CCCipher {
        val key = getKeyForName(keyName)

        return CCCipher.phoenixCipherForKey(key)
    }
}