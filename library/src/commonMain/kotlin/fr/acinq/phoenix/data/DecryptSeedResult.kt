package fr.acinq.phoenix.data

sealed class DecryptSeedResult {
    data class Success(val userWalletsMap: Map<WalletId, UserWallet>): DecryptSeedResult()
    sealed class Failure: DecryptSeedResult() {
        data object SeedFileNotFound: Failure()
        data object SerializationError: Failure()
        data class KeyStoreFailure(val cause: Throwable): Failure()
        data class DecryptionError(val cause: Throwable): Failure()
        data object SeedFileUnreadable: Failure()
        data object SeedInvalid: Failure()
    }
}
