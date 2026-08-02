package fr.acinq.phoenix.data

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.WalletManager

sealed class StartBusinessResult {
    data class Success(val walletInfo: WalletManager.WalletInfo, val business: PhoenixBusiness): StartBusinessResult()
    sealed class Failure : StartBusinessResult() {
        data class Generic(val cause: Throwable): Failure()
        data object LoadWalletError: Failure()
    }
}
