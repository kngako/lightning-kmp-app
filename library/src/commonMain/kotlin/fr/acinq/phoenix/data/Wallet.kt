package fr.acinq.phoenix.data

import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.UserPrefs
import kotlinx.serialization.Serializable


sealed class ListWalletState {
    data object Init: ListWalletState()
    data object Success: ListWalletState()
    sealed class Error: ListWalletState() {
        data class Generic(val cause: Throwable?): Error()
        data object Serialization: Error()

        sealed class DecryptionError : Error() {
            data class GeneralException(val cause: Throwable): DecryptionError()
            data class KeystoreFailure(val cause: Throwable): DecryptionError()
        }
    }
}

sealed class BaseWalletId
data object EmptyWalletId: BaseWalletId()
/** Wraps a nodeIdHash (hash160 of a nodeId). Easier to maintain and upgrade than a plain String. */
@Serializable
data class WalletId(val nodeIdHash: String): BaseWalletId() {
    constructor(nodeId: PublicKey) : this(
        nodeIdHash = nodeId.hash160().byteVector().toHex()
    )
    override fun toString() = nodeIdHash
    override fun hashCode() = nodeIdHash.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletId) return false
        return nodeIdHash == other.nodeIdHash
    }
}

data class UserWallet(
    val walletId: WalletId,
    val nodeId: String,
    val words: List<String>,
) {
    override fun toString(): String = "UserWallet[ wallet_id=$walletId, words=*** ]"
}

data class ActiveWallet(
    val id: WalletId,
    val business: PhoenixBusiness?,
    val userPrefs: UserPrefs,
    val internalPrefs: InternalPrefs,
)