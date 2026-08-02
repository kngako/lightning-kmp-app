package fr.acinq.phoenix.data

import androidx.compose.runtime.Composable
import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.library.generated.resources.Res
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_closing_channel
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_cpfp
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_liquidity_automated
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_liquidity_manual
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_splice_out
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_swapin
import fr.acinq.phoenix.library.generated.resources.paymentdetails_desc_swapout
import fr.acinq.phoenix.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.utils.extensions.desc
import org.jetbrains.compose.resources.stringResource

/**
 * Represents a payment & its associated metadata.
 */
data class WalletPaymentInfo(
    val payment: WalletPayment,
    val metadata: WalletPaymentMetadata,
    val contact: ContactInfo?
) {
    val id get() = payment.id
}

/** Returns true if the payment is a channel-close made by the legacy app to the node's swap-in address. */
fun WalletPayment.isLegacyMigration(metadata: WalletPaymentMetadata): Boolean? {
    return when {
        this !is ChannelCloseOutgoingPayment -> false
        metadata.userDescription == "kmp-migration-override" -> true
        else -> false
    }
}

/**
 * Returns a trimmed, localized description of the payment, based on the type and information available. May be null!
 *
 * For example, a payment closing a channel has no description, and it's up to us to create one. Others like a LN
 * payment with an invoice do have a description baked in, and that's what is returned.
 */
@Composable
fun WalletPayment.smartDescription(): String? = when (this) {
    is LightningOutgoingPayment -> smartDescription()
    is IncomingPayment -> smartDescription()
    is ChannelCloseOutgoingPayment -> smartDescription()
    is SpliceOutgoingPayment -> smartDescription()
    is SpliceCpfpOutgoingPayment -> smartDescription()
    is ManualLiquidityPurchasePayment -> smartDescription()
    is AutomaticLiquidityPurchasePayment -> smartDescription()
}


@Composable
fun LightningOutgoingPayment.smartDescription(): String? = when (val details = this.details) {
    is LightningOutgoingPayment.Details.Normal -> details.paymentRequest.desc
    is LightningOutgoingPayment.Details.SwapOut -> stringResource(Res.string.paymentdetails_desc_swapout, details.address)
    is LightningOutgoingPayment.Details.Blinded -> details.paymentRequest.description
}?.takeIf { it.isNotBlank() }

@Composable
fun SpliceOutgoingPayment.smartDescription(): String = stringResource(Res.string.paymentdetails_desc_splice_out)

@Composable
fun SpliceCpfpOutgoingPayment.smartDescription(): String = stringResource(Res.string.paymentdetails_desc_cpfp)

@Composable
fun ChannelCloseOutgoingPayment.smartDescription(): String = stringResource(Res.string.paymentdetails_desc_closing_channel)

@Composable
fun ManualLiquidityPurchasePayment.smartDescription(): String =
    stringResource(
        Res.string.paymentdetails_desc_liquidity_manual,
        liquidityPurchase.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true)
    )

@Composable
fun AutomaticLiquidityPurchasePayment.smartDescription(): String =
    stringResource(
        Res.string.paymentdetails_desc_liquidity_automated,
        liquidityPurchase.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true)
    )

@Suppress("DEPRECATION")
@Composable
fun IncomingPayment.smartDescription() : String? = when (this) {
    is Bolt11IncomingPayment -> paymentRequest.description
    is Bolt12IncomingPayment -> null
    is OnChainIncomingPayment -> stringResource(Res.string.paymentdetails_desc_swapin)
    is LegacySwapInIncomingPayment -> stringResource(Res.string.paymentdetails_desc_swapin)
    is LegacyPayToOpenIncomingPayment -> when (val origin = origin) {
        is LegacyPayToOpenIncomingPayment.Origin.Invoice -> origin.paymentRequest.description
        is LegacyPayToOpenIncomingPayment.Origin.Offer -> null
    }
}?.takeIf { it.isNotBlank() }



@Suppress("DEPRECATION")
fun WalletPayment.basicDescription(): String? = when (this) {
    is Bolt11IncomingPayment -> paymentRequest.description?.takeIf { it.isNotBlank() }
    is LegacyPayToOpenIncomingPayment -> when (val origin = origin) {
        is LegacyPayToOpenIncomingPayment.Origin.Invoice -> origin.paymentRequest.description
        is LegacyPayToOpenIncomingPayment.Origin.Offer -> null
    }
    is IncomingPayment -> null
    is LightningOutgoingPayment -> when (val details = this.details) {
        is LightningOutgoingPayment.Details.Normal -> details.paymentRequest.desc
        is LightningOutgoingPayment.Details.SwapOut -> null
        is LightningOutgoingPayment.Details.Blinded -> details.paymentRequest.description
    }
    is ChannelCloseOutgoingPayment -> null
    is SpliceOutgoingPayment -> null
    is SpliceCpfpOutgoingPayment -> null
    is ManualLiquidityPurchasePayment -> null
    is AutomaticLiquidityPurchasePayment -> null
}?.takeIf { it.isNotBlank() }


/**
 * Represents information from the `payments_metadata` table.
 */
data class WalletPaymentMetadata(
    val lnurl: LnurlPayMetadata? = null,
    val originalFiat: ExchangeRate.BitcoinPriceRate? = null,
    val userDescription: String? = null,
    val userNotes: String? = null,
    val lightningAddress: String? = null,
    val modifiedAt: Long? = null
)

data class LnurlPayMetadata(
    val pay: LnurlPay.Intent,
    val description: String,
    val successAction: LnurlPay.Invoice.SuccessAction?
) {
    companion object { /* allow companion extensions */ }
}
