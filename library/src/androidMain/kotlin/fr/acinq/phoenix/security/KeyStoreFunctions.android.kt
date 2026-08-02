package fr.acinq.phoenix.security

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.utils.extensions.tryWith
import java.security.GeneralSecurityException

actual fun keyStoreDecryption(
    keyName: String,
    iv: ByteArray,
    ciphertext: ByteArray
): ByteArray {
    Logger.withTag("KeyStoreFunctions").i("IV length: ${iv.size}")
    return KeystoreHelper.getDecryptionCipher(keyName, iv).doFinal(ciphertext)
}

actual fun keyStoreEncryption(keyName: String, plainText: ByteArray): Pair<ByteArray, ByteArray> = tryWith(GeneralSecurityException()) {
    val cipher = KeystoreHelper.getEncryptionCipher(keyName)
    Logger.withTag("KeyStoreFunctions").i("IV for encryption: ${cipher.iv.size}")
    return Pair(
        cipher.iv,
        cipher.doFinal(plainText)
    )
}