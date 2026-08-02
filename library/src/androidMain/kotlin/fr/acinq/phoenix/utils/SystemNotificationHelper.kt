/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.compose.AppVersion
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.library.generated.resources.Res
import fr.acinq.phoenix.library.generated.resources.notif_watcher_revoked_commit_message
import fr.acinq.phoenix.library.generated.resources.notif_watcher_revoked_commit_title
import fr.acinq.phoenix.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.utils.converters.DateFormatter.toAbsoluteDateString
import fr.acinq.phoenix.utils.preferences.UserPrefs
import fr.acinq.phoenix.utils.preferences.UserWalletMetadata
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.slf4j.LoggerFactory
import java.text.DecimalFormat

object SystemNotificationHelper {
    private const val PAYMENT_FAILED_NOTIF_ID = 354319
    private val PAYMENT_FAILED_NOTIF_CHANNEL = "${AppVersion.applicationId()}.PAYMENT_FAILED_NOTIF"
    private val PAYMENT_RECEIVED_NOTIF_CHANNEL = "${AppVersion.applicationId()}.PAYMENT_RECEIVED_NOTIF"

    private const val SETTLEMENT_PENDING_NOTIF_ID = 354322
    private val SETTLEMENT_PENDING_NOTIF_CHANNEL = "${AppVersion.applicationId()}.SETTLEMENT_PENDING_NOTIF"

    const val HEADLESS_NOTIF_ID = 354321
    val HEADLESS_NOTIF_CHANNEL = "${AppVersion.applicationId()}.BACKGROUND_PROCESSING"

    private const val CHANNELS_WATCHER_ALERT_ID = 354324
    private val CHANNELS_WATCHER_ALERT_CHANNEL = "${AppVersion.applicationId()}.CHANNELS_WATCHER"

    private const val SWAP_TIMEOUT_ID = 354325
    private val SWAP_TIMEOUT_CHANNEL = "${AppVersion.applicationId()}.SWAP_TIMEOUT"

    private val log = LoggerFactory.getLogger(this::class.java)

    /** If the remaining blocks count before a swap timeout is lower than this, we should mention it in the notification. */
    private const val SWAP_TIMEOUT_THRESHOLD_IN_BLOCKS = 144 * 30  * 2 // ~2 months

    fun registerNotificationChannels(context: Context) {
        // notification channels (android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(
                listOf(
                    // TODO: Setup notifications...
//                    NotificationChannel(HEADLESS_NOTIF_CHANNEL, context.getString(R.string.notification_headless_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
//                        description = context.getString(R.string.notification_headless_desc)
//                    },
//                    NotificationChannel(CHANNELS_WATCHER_ALERT_CHANNEL, context.getString(R.string.notification_channels_watcher_title), NotificationManager.IMPORTANCE_HIGH).apply {
//                        description = context.getString(R.string.notification_channels_watcher_desc)
//                    },
//                    NotificationChannel(SETTLEMENT_PENDING_NOTIF_CHANNEL, context.getString(R.string.notification_pending_settlement_title), NotificationManager.IMPORTANCE_HIGH).apply {
//                        description = context.getString(R.string.notification_pending_settlement_desc)
//                    },
//                    NotificationChannel(PAYMENT_RECEIVED_NOTIF_CHANNEL, context.getString(R.string.notification_received_payment_title), NotificationManager.IMPORTANCE_LOW).apply {
//                        description = context.getString(R.string.notification_received_payment_desc)
//                    },
//                    NotificationChannel(PAYMENT_FAILED_NOTIF_CHANNEL, context.getString(R.string.notification_missed_payment_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
//                        description = context.getString(R.string.notification_missed_payment_desc)
//                    },
//                    NotificationChannel(SWAP_TIMEOUT_CHANNEL, context.getString(R.string.notification_swap_timeout_title), NotificationManager.IMPORTANCE_HIGH).apply {
//                        description = context.getString(R.string.notification_swap_timeout_desc)
//                    },
                )
            )
        }
    }

    private fun implementationPendingNotification(context: Context, text: String): Notification {
        return NotificationCompat.Builder(context, HEADLESS_NOTIF_CHANNEL).apply {
//          TODO:  setContentTitle(context.getString(Res.string.notif_headless_title_default))
            setContentTitle("$text implementation pending")
//          TODO:  setSmallIcon(R.drawable.ic_phoenix_outline)
        }.build().also {
            // TODO: Implement notifications
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(HEADLESS_NOTIF_ID, it)
//            }
        }
    }
    fun notifyRunningHeadless(context: Context): Notification {
        return NotificationCompat.Builder(context, HEADLESS_NOTIF_CHANNEL).apply {
//          TODO:  setContentTitle(context.getString(Res.string.notif_headless_title_default))
            setContentTitle("Running Headless")
//          TODO:  setSmallIcon(R.drawable.ic_phoenix_outline)
        }.build().also {
            // TODO: Implement notifications
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(HEADLESS_NOTIF_ID, it)
//            }
        }
    }

    private fun notifyPaymentFailed(context: Context, walletMetadata: UserWalletMetadata, title: String, message: String, deepLink: String?): Notification {
        return NotificationCompat.Builder(context, PAYMENT_FAILED_NOTIF_CHANNEL).apply {
            setContentTitle(getTitleForWallet(walletMetadata, title))
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
//          TODO:  setSmallIcon(R.drawable.ic_phoenix_outline)
//            val intent = deepLink?.let {
//                Intent(Intent.ACTION_VIEW, it.toUri(), context, MainActivity::class.java)
//            } ?: Intent(context, MainActivity::class.java)
//            setContentIntent(
//                TaskStackBuilder.create(context).run {
//                    addNextIntentWithParentStack(intent)
//                    getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
//                }
//            )

            setAutoCancel(true)
        }.build().also {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//               TODO: NotificationManagerCompat.from(context).notify(PAYMENT_FAILED_NOTIF_ID, it)
            }
        }
    }

    private fun getTitleForWallet(walletMetadata: UserWalletMetadata, title: String): String {
        return "${walletMetadata.avatar} $title"
    }

    fun notifyPaymentRejectedPolicyDisabled(context: Context, walletId: WalletId, walletMetadata: UserWalletMetadata, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi, nextTimeoutRemainingBlocks: Int?): Notification {
        return notifyPaymentFailed(
            context = context,
            title = "Payment rejected policy disabled",
//          TODO:  title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
//                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
            message = when {
                source == LiquidityEvents.Source.OnChainWallet && nextTimeoutRemainingBlocks != null && nextTimeoutRemainingBlocks < SWAP_TIMEOUT_THRESHOLD_IN_BLOCKS -> {
                    nextTimeoutRemainingBlocks * 10 * DateUtils.MINUTE_IN_MILLIS
//                  TODO:  context.getString(R.string.notif_rejected_policy_disabled_timeout, (currentTimestampMillis() + remainingTimeMillis).toAbsoluteDateString())
                    "Rejected policy disabled timeout"
                }
                else -> {
//                  TODO:  context.getString(R.string.notif_rejected_policy_disabled)
                    "Rejected policy disabled"
                }
            },
            walletMetadata = walletMetadata,
            deepLink = if (source == LiquidityEvents.Source.OnChainWallet) "phoenix:swapinwallet/$walletId" else "phoenix:notifications/$walletId",
        )
    }

    fun notifyPaymentRejectedOverAbsolute(context: Context, walletId: WalletId, walletMetadata: UserWalletMetadata, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi, fee: MilliSatoshi, absoluteMax: Satoshi, nextTimeoutRemainingBlocks: Int?): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentRejectedOverAbsolute"
        )
//        return notifyPaymentFailed(
//            context = context,
//            title = getTitleForWallet(walletMetadata, context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
//                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true))),
//            message = "TODO: Messaging",
////            when {
////                source == LiquidityEvents.Source.OnChainWallet && nextTimeoutRemainingBlocks != null && nextTimeoutRemainingBlocks < SWAP_TIMEOUT_THRESHOLD_IN_BLOCKS -> {
////                    val remainingTimeMillis = nextTimeoutRemainingBlocks * 10 * DateUtils.MINUTE_IN_MILLIS
////                    context.getString(
////                        Res.string.notif_rejected_over_absolute_timeout, fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
////                        absoluteMax.toPrettyString(BitcoinUnit.Sat, withUnit = true), (currentTimestampMillis() + remainingTimeMillis).toAbsoluteDateString()
////                    )
////                }
////                else -> {
////                    context.getString(R.string.notif_rejected_over_absolute, fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
////                        absoluteMax.toPrettyString(BitcoinUnit.Sat, withUnit = true))
////                }
////            },
//            walletMetadata = walletMetadata,
//            deepLink = if (source == LiquidityEvents.Source.OnChainWallet) "phoenix:swapinwallet/$walletId" else "phoenix:notifications/$walletId",
//        )
    }

    fun notifyPaymentRejectedOverRelative(context: Context, walletId: WalletId, walletMetadata: UserWalletMetadata, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi, fee: MilliSatoshi, percentMax: Int, nextTimeoutRemainingBlocks: Int?): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentRejectedOverRelative"
        )
//        return notifyPaymentFailed(
//            context = context,
//            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
//                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
//            message = when {
//                source == LiquidityEvents.Source.OnChainWallet && nextTimeoutRemainingBlocks != null && nextTimeoutRemainingBlocks < SWAP_TIMEOUT_THRESHOLD_IN_BLOCKS -> {
//                    val remainingTimeMillis = nextTimeoutRemainingBlocks * 10 * DateUtils.MINUTE_IN_MILLIS
//                    context.getString(R.string.notif_rejected_over_relative_timeout, fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
//                        DecimalFormat("0.##").format(percentMax.toDouble() / 100), (currentTimestampMillis() + remainingTimeMillis).toAbsoluteDateString()
//                    )
//                }
//                else -> {
//                    context.getString(R.string.notif_rejected_over_relative, fee.toPrettyString(BitcoinUnit.Sat, withUnit = true),
//                        DecimalFormat("0.##").format(percentMax.toDouble() / 100))
//                }
//            },
//            walletMetadata = walletMetadata,
//            deepLink = if (source == LiquidityEvents.Source.OnChainWallet) "phoenix:swapinwallet/$walletId" else "phoenix:notifications/$walletId",
//        )
    }

    fun notifyPaymentRejectedAmountTooLow(context: Context, walletId: WalletId, walletMetadata: UserWalletMetadata, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentRejectedAmountTooLow"
        )
//        return notifyPaymentFailed(
//            context = context,
//            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
//                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
//            message = context.getString(R.string.notif_rejected_amount_too_low),
//            walletMetadata = walletMetadata,
//            deepLink = if (source == LiquidityEvents.Source.OnChainWallet) "phoenix:swapinwallet/$walletId" else "phoenix:notifications/$walletId",
//        )
    }

    fun notifyPaymentRejectedFundingError(context: Context, walletId: WalletId, walletMetadata: UserWalletMetadata, source: LiquidityEvents.Source, amountIncoming: MilliSatoshi): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentRejectedFundingError"
        )
//        return notifyPaymentFailed(
//            context = context,
//            title = context.getString(if (source == LiquidityEvents.Source.OnChainWallet) R.string.notif_rejected_deposit_title else R.string.notif_rejected_payment_title,
//                amountIncoming.toPrettyString(BitcoinUnit.Sat, withUnit = true)),
//            message = context.getString(R.string.notif_rejected_generic_error),
//            walletMetadata = walletMetadata,
//            deepLink = if (source == LiquidityEvents.Source.OnChainWallet) "phoenix:swapinwallet/$walletId" else "phoenix:notifications/$walletId",
//        )
    }

    fun notifyPaymentMissedAppUnavailable(context: Context, walletMetadata: UserWalletMetadata): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentMissedAppUnavailable"
        )
//        return notifyPaymentFailed(
//            context = context,
//            title = context.getString(R.string.notif_missed_title),
//            message = context.getString(R.string.notif_missed_unavailable),
//            walletMetadata = walletMetadata,
//            deepLink = null
//        )
    }

    fun notifyPendingSettlement(context: Context, walletMetadata: UserWalletMetadata): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPendingSettlement"
        )
//        return NotificationCompat.Builder(context, SETTLEMENT_PENDING_NOTIF_CHANNEL).apply {
//            setContentTitle(getTitleForWallet(walletMetadata, context.getString(R.string.notif_pending_settlement_title)))
//            setContentText(context.getString(R.string.notif_pending_settlement_message))
//            setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_pending_settlement_message)))
//            setSmallIcon(R.drawable.ic_launcher_foreground)
//            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
//            setAutoCancel(true)
//        }.build().also {
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(SETTLEMENT_PENDING_NOTIF_ID, it)
//            }
//        }
    }

    fun notifyInFlightHtlc(context: Context, walletMetadata: UserWalletMetadata): Notification {
        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentRejectedOverAbsolute"
        )
//        return NotificationCompat.Builder(context, SETTLEMENT_PENDING_NOTIF_CHANNEL).apply {
//            setContentTitle(getTitleForWallet(walletMetadata, context.getString(R.string.notif_inflight_payment_title)))
//            setContentText(context.getString(R.string.notif_inflight_payment_message))
//            setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_inflight_payment_message)))
//            setSmallIcon(R.drawable.ic_launcher_foreground)
//            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
//            setAutoCancel(true)
//        }.build().also {
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(SETTLEMENT_PENDING_NOTIF_ID, it)
//            }
//        }
    }

    suspend fun notifyPaymentsReceived(
        context: Context,
        userPrefs: UserPrefs,
        walletId: WalletId,
        userWalletMetadata: UserWalletMetadata,
        paymentId: UUID,
        paymentAmount: MilliSatoshi,
        rates: List<ExchangeRate>,
    ): Notification {
        val isFiat = userPrefs.getIsAmountInFiat.first() && rates.isNotEmpty()
        val unit = if (isFiat) {
            userPrefs.getFiatCurrencies.first().primary
        } else {
            userPrefs.getBitcoinUnits.first().primary
        }
        val rate = if (isFiat) {
            when (val rate = rates.find { it.fiatCurrency == unit }) {
                is ExchangeRate.BitcoinPriceRate -> rate
                is ExchangeRate.UsdPriceRate -> {
                    (rates.find { it.fiatCurrency == FiatCurrency.USD } as? ExchangeRate.BitcoinPriceRate)?.let { usdRate ->
                        // create a BTC/Fiat price rate using the USD/BTC rate and the Fiat/USD rate.
                        ExchangeRate.BitcoinPriceRate(
                            fiatCurrency = rate.fiatCurrency,
                            price = rate.price * usdRate.price,
                            source = rate.source,
                            timestampMillis = rate.timestampMillis
                        )
                    }
                }
                else -> null
            }
        } else null

        return implementationPendingNotification(
            context = context,
            text = "notifyPaymentsReceived"
        )

//        return NotificationCompat.Builder(context, PAYMENT_RECEIVED_NOTIF_CHANNEL).apply {
//            setContentTitle(context.getString(R.string.notif_headless_received, userWalletMetadata.avatar, paymentAmount.toPrettyString(unit, rate, withUnit = true)))
//            setSmallIcon(R.drawable.ic_launcher_foreground)
//            val intent = Intent(Intent.ACTION_VIEW,"phoenix:payments/$walletId/$paymentId".toUri(), context, MainActivity::class.java).apply {
//                Intent.FLAG_ACTIVITY_SINGLE_TOP
//            }
//            setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
//            setAutoCancel(true)
//        }.build().also {
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(currentTimestampMillis().toInt(), it)
//            }
//        }
    }

    fun notifyRevokedCommits(context: Context) {
        // TODO:

//        NotificationCompat.Builder(context, CHANNELS_WATCHER_ALERT_CHANNEL).apply {
//            setContentTitle(stringResource(Res.string.notif_watcher_revoked_commit_title))
//            setContentText(stringResource(Res.string.notif_watcher_revoked_commit_message))
//            setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
//            setAutoCancel(true)
//        }.let {
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
//                NotificationManagerCompat.from(context).notify(CHANNELS_WATCHER_ALERT_ID, it.build())
//            }
//        }
    }

}