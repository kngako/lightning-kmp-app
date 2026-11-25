package fr.acinq.phoenix.data

/** What reading `recovery-codes.dat` found. The failures are [DecryptNostrCredentialsResult]'s, for the same reasons. */
sealed class DecryptRecoveryCodesResult {
    /** Each recovery code this device holds, by the id of the profile it backs. */
    data class Success(val codes: Map<WalletId, String>) : DecryptRecoveryCodesResult()

    sealed class Failure : DecryptRecoveryCodesResult() {
        data object FileNotFound : Failure()
        data object SerializationError : Failure()
        data class KeyStoreFailure(val cause: Throwable) : Failure()
        data class DecryptionError(val cause: Throwable) : Failure()
        data object FileUnreadable : Failure()
    }
}
