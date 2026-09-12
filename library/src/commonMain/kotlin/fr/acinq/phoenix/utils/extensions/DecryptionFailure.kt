package fr.acinq.phoenix.utils.extensions

// Its own file rather than an addition to TechnicalExtensions.kt: androidMain has a
// TechnicalExtensions.kt of its own in this package, and a common file of the same name
// may hold only `expect` declarations -- those generate no facade class. Anything with a
// body here would be a second `TechnicalExtensionsKt` and the android target refuses it.

/**
 * Whether [e] means the platform key store could not serve its key, as opposed to the
 * payload being wrong. On the jvm and android that is `java.security.KeyStoreException`;
 * ios has no such type, and its keychain helper surfaces nothing that is usefully told
 * apart from a bad payload, so it answers false.
 *
 * The one platform-specific fact behind [classifyDecryptionFailure]. The
 * `graceful*SeedDecryption` functions predate it and carry the same branching inline,
 * per platform.
 */
expect fun isKeyStoreFailure(e: Throwable): Boolean

/** How a decryption failure should be reported, independent of which file was being read. */
enum class DecryptionFailure {
    /** The bytes came back but were not the shape expected: a corrupt or foreign file. */
    Serialization,

    /** The key store itself failed; the file may be fine. */
    KeyStore,

    /** Anything else: a bad tag, a truncated ciphertext, an unexpected throw. */
    Other,
}

/**
 * Classifies a throw from a read-and-decrypt so that every encrypted file this library
 * keeps reports failure the same way. `SerializationException` is the JSON not parsing;
 * `IllegalArgumentException` is what `require` and the deserializers throw for a wrong
 * version byte or an entry that fails its own check.
 */
fun classifyDecryptionFailure(e: Exception): DecryptionFailure = when {
    e is kotlinx.serialization.SerializationException -> DecryptionFailure.Serialization
    e is IllegalArgumentException -> DecryptionFailure.Serialization
    isKeyStoreFailure(e) -> DecryptionFailure.KeyStore
    else -> DecryptionFailure.Other
}
