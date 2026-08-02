package fr.acinq.phoenix.security

expect fun keyStoreDecryption(keyName: String, iv: ByteArray, ciphertext: ByteArray): ByteArray

expect fun keyStoreEncryption(keyName: String, plainText: ByteArray): Pair<ByteArray, ByteArray>