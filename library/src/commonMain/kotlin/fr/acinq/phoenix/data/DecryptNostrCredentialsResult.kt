package fr.acinq.phoenix.data

import fr.acinq.phoenix.security.NostrCredential

/** The outcome of reading `nostr-credentials.dat`; the shape of [DecryptSeedResult], for the sibling file. */
sealed class DecryptNostrCredentialsResult {
    /** Credentials by x-only public key, hex. */
    data class Success(val credentials: Map<String, NostrCredential>) : DecryptNostrCredentialsResult()

    sealed class Failure : DecryptNostrCredentialsResult() {
        data object FileNotFound : Failure()
        data object SerializationError : Failure()
        data class KeyStoreFailure(val cause: Throwable) : Failure()
        data class DecryptionError(val cause: Throwable) : Failure()
        data object FileUnreadable : Failure()
    }
}
