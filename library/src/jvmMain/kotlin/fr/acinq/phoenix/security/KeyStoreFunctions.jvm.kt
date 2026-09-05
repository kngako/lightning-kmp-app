package fr.acinq.phoenix.security

/**
 * Backed by [JvmKeyStore], which derives its key from a passphrase the embedding
 * application supplies through `JvmKeyStore.unlock` before any seed is read or written.
 *
 * Both throw `java.security.KeyStoreException` while the store is locked, which is the same
 * type android's `AndroidKeyStore` raises when it cannot serve a key, and which
 * `gracefulSingleSeedDecryption`/`gracefulMultiSeedDecryption` already map to
 * `DecryptSeedResult.Failure.KeyStoreFailure`. A caller that forgets to unlock therefore
 * gets the failure it would get from a broken keystore, rather than a crash.
 *
 * **This has no hardware backing.** See the class documentation on [JvmKeyStore] for what
 * that costs and why a desktop build holding real funds wants an OS keychain instead.
 */
actual fun keyStoreDecryption(keyName: String, iv: ByteArray, ciphertext: ByteArray): ByteArray =
    JvmKeyStore.decrypt(keyName, iv, ciphertext)

/** Returns the iv and the ciphertext, in that order, matching the android actual. */
actual fun keyStoreEncryption(keyName: String, plainText: ByteArray): Pair<ByteArray, ByteArray> =
    JvmKeyStore.encrypt(keyName, plainText)
