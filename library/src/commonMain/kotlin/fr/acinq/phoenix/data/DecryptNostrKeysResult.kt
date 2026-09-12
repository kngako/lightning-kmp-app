package fr.acinq.phoenix.data

import fr.acinq.bitcoin.PrivateKey

/** The outcome of reading `nostr-keys.dat`; the shape of [DecryptSeedResult], for the sibling file. */
sealed class DecryptNostrKeysResult {
    /** Keys by x-only public key, hex. */
    data class Success(val keys: Map<String, PrivateKey>) : DecryptNostrKeysResult()

    sealed class Failure : DecryptNostrKeysResult() {
        data object FileNotFound : Failure()
        data object SerializationError : Failure()
        data class KeyStoreFailure(val cause: Throwable) : Failure()
        data class DecryptionError(val cause: Throwable) : Failure()
        data object FileUnreadable : Failure()
    }
}
