package fr.acinq.phoenix.security

/**
 * **Not implemented, on purpose. These throw.**
 *
 * Android backs these with `AndroidKeyStore` via `KeystoreHelper`, StrongBox-backed where
 * the device has a secure element, and the key material never leaves hardware. The jvm has
 * no portable equivalent, so choosing what replaces it -- a passphrase-derived KEK, an OS
 * keychain through JNA, or a key file -- is a security decision about wallet seed material
 * rather than a port. That decision is phase 3 of docs/jvm-target.md in the consuming
 * repository, and it has not been made.
 *
 * These exist so the module compiles and the rest of the jvm target can be tested. A
 * loud failure is the point: the alternative is a placeholder that appears to work while
 * storing a seed weakly, and that is the one outcome worth ruling out. Nothing on this
 * platform can read or write a seed until they are replaced.
 */
actual fun keyStoreDecryption(keyName: String, iv: ByteArray, ciphertext: ByteArray): ByteArray =
    throw NotImplementedError(UNIMPLEMENTED)

actual fun keyStoreEncryption(keyName: String, plainText: ByteArray): Pair<ByteArray, ByteArray> =
    throw NotImplementedError(UNIMPLEMENTED)

private const val UNIMPLEMENTED =
    "jvm key storage is not implemented; see phase 3 of docs/jvm-target.md. " +
        "This build cannot read or write a wallet seed."
