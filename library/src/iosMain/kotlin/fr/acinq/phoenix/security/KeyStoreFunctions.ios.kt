package fr.acinq.phoenix.security

import fr.acinq.lightning.Lightning

actual fun keyStoreDecryption(
    keyName: String,
    iv: ByteArray,
    ciphertext: ByteArray
): ByteArray = KeyChainHelper.getDecryptionCipher(
    keyName = keyName,
).decrypt(
    iv,
    ciphertext,
    0
)

actual fun keyStoreEncryption(keyName: String, plainText: ByteArray): Pair<ByteArray, ByteArray> {
    val iv = Lightning.randomBytes(16)
    val cipherText = KeyChainHelper.getEncryptionCipher(keyName).encrypt(
        iv,
        plainText
    )

    return Pair(iv, cipherText)
}