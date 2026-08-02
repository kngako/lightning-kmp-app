package fr.acinq.phoenix.ios

import co.touchlab.kermit.Logger
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.PaymentEvents
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.BusinessMonitorJobs
import fr.acinq.phoenix.BusinessRunning
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.compose.AppVersion
import fr.acinq.phoenix.compose.ui.composable.widgets.wallet.WalletAvatars
import fr.acinq.phoenix.data.StartBusinessResult
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.data.inFlightPaymentsCount
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.global.CurrencyManager
import fr.acinq.phoenix.utils.DateUtils
import fr.acinq.phoenix.utils.MnemonicLanguage
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.preferences.GlobalPrefs
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.UserPrefs
import fr.acinq.phoenix.utils.preferences.UserWalletMetadata
import fr.acinq.phoenix.utils.preferences.getByWalletIdOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object BusinessManager {
    private val log = Logger.withTag("BusinessManager")
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + supervisor)

    private val startupMutex = Mutex()


    val phoenixGlobal: PhoenixGlobal = PhoenixGlobal(
        ctx = PlatformContext()
    )



    /** A map of (walletId -> active businesses) */
    private val _businessFlow = MutableStateFlow<Map<WalletId, BusinessRunning>>(emptyMap())
    val businessFlow = _businessFlow.asStateFlow()

    /** Map of jobs monitoring events/payments once business starts */
    private val eventsMonitoringJobs = mutableMapOf<WalletId, BusinessMonitorJobs>() //List<Job>>()

    suspend fun startNewBusiness(words: List<String>, isHeadless: Boolean): StartBusinessResult = startupMutex.withLock {
        val business = PhoenixBusiness(phoenixGlobal)

        val walletInfo = try {
            val seed = business.walletManager.mnemonicsToSeed(words, wordList = MnemonicLanguage.English.wordlist())
            business.walletManager.loadWallet(seed)
        } catch (e: Exception) {
            return StartBusinessResult.Failure.LoadWalletError
        }

        val walletId = WalletId(walletInfo.nodeIdHash)
        val nodeId = walletInfo.nodeId.toHex()

        val dataStoreManager = business.dataStoreManager

        val globalPrefs: GlobalPrefs = dataStoreManager.loadGlobalPrefsForWallet()

        val walletMetadata = globalPrefs.getAvailableWalletsMeta.first()[walletId] ?: run {
            val metadata = UserWalletMetadata(
                walletId = walletId,
                name = null,
                avatar = WalletAvatars.list.random(),
                createdAt = currentTimestampMillis(),
                isHidden = false
            )
            globalPrefs.saveAvailableWalletMeta(metadata)
            metadata
        }
        val userPrefs = dataStoreManager.loadUserPrefsForWallet(walletId)
        val internalPrefs = dataStoreManager.loadInternalPrefsForWallet(walletId)

        val businessInFlow = businessFlow.value[walletId]?.business
        if (businessInFlow != null) {
            log.i { "business already exists in flow, ignoring..." }
            return StartBusinessResult.Success(walletInfo, businessInFlow)
        }


        return try {
            log.i("preparing new business with node_id=$nodeId wallet_id=$walletId...")

            // check last used version to display a patch note
            val lastVersionUsed = globalPrefs.getLastUsedAppCode.first()
            if (lastVersionUsed == null) {
                // lastUsedAppCode was added in version 99, and is set up during the wallet creation. So if it's null, this Phoenix was installed prior v99 and we can show a patch note
                globalPrefs.saveShowReleaseNoteSinceCode("98")
            }
            else if (lastVersionUsed < AppVersion.versionCode) {
                globalPrefs.saveShowReleaseNoteSinceCode(lastVersionUsed)
            }

            // update app configuration with user preferences
            business.appConfigurationManager.updateElectrumConfig(userPrefs.getElectrumServer.first())
            val preferredCurrencies = userPrefs.getFiatCurrencies.first()
            business.appConfigurationManager.updatePreferredFiatCurrencies(preferredCurrencies)
            business.phoenixGlobal.currencyManager.startMonitoringCurrencies(walletId = walletId.nodeIdHash, currencies = preferredCurrencies)

            // setup jobs monitoring the business events
            eventsMonitoringJobs[walletId] = BusinessMonitorJobs(
                monitorHeadlessPaymentsJob = if (isHeadless) {
                    scope.launch { monitorPaymentsWhenHeadless(walletId, walletMetadata, business.nodeParamsManager, phoenixGlobal.currencyManager, userPrefs) }
                } else null,
                monitorNodeEventsJob = scope.launch { monitorNodeEvents(walletId, business.peerManager, business.nodeParamsManager, globalPrefs, internalPrefs) },
                monitorFcmTokenJob = scope.launch { monitorFcmToken(globalPrefs, business) },
                monitorInFlightPaymentsJob = scope.launch { monitorInFlightPayments(business.peerManager, internalPrefs) },
            )

            // startup params depend user's settings: Tor and liquidity policy
            val startupParams = StartupParams(isTorEnabled = userPrefs.getIsTorEnabled.first(), liquidityPolicy = userPrefs.getLiquidityPolicy.first())
            delay(1_000)

            // actually start the business
            log.i("starting new business with node_id=$nodeId...")
            _businessFlow.value += walletId to BusinessRunning(business = business, isHeadless = isHeadless)
            business.start(startupParams)

            // the node has been started, so we can now increment the last-used build code
            globalPrefs.saveLastUsedAppCode(AppVersion.versionCode)

            // start watching the swap-in wallet
            scope.launch {
                business.peerManager.getPeer().startWatchSwapInWallet()
            }

            log.i("business initialisation has successfully completed")
            StartBusinessResult.Success(walletInfo, business)
        } catch (e: Exception) {
            log.e("there was an error when initialising new business: ", e)
            stopBusiness(walletId)
            StartBusinessResult.Failure.Generic(e)
        }
    }


    /**
     * Updates the matching business in the map of active businesses with a non-headless flag. Should be called when the UI starts a given wallet.
     * If called improperly, will not have severe effects ; the app will just show incoming payment notifications.
     */
    fun updateBusinessActiveInUI(walletId: WalletId) {
        val businessMap = _businessFlow.value.toMutableMap()
        businessMap[walletId]?.let {
            businessMap[walletId] = it.copy(isHeadless = false)
        }
        eventsMonitoringJobs[walletId]?.monitorHeadlessPaymentsJob?.cancel()
        _businessFlow.value = businessMap
    }

    fun stopAllHeadlessBusinesses() {
        val headlessBusinesses = businessFlow.value.filter { it.value.isHeadless }
        log.i("stopping all headless businesses (${headlessBusinesses.size})...")
        headlessBusinesses.forEach { doStopBusiness(it.key, it.value) }
        _businessFlow.value = businessFlow.value.minus(headlessBusinesses.keys)
    }

    fun stopAllBusinesses() {
        log.i("stopping all businesses...")
        businessFlow.value.forEach { doStopBusiness(it.key, it.value) }
        _businessFlow.value = emptyMap()
    }

    fun stopBusiness(walletId: WalletId) {
        val businessMap = _businessFlow.value.toMutableMap()
        businessMap[walletId]?.let { doStopBusiness(walletId, it)}
        businessMap.remove(walletId)
        _businessFlow.value = businessMap
    }

    private fun doStopBusiness(walletId: WalletId, running: BusinessRunning) {
        running.business.appConnectionsDaemon?.incrementDisconnectCount(AppConnectionsDaemon.ControlTarget.All)
        running.business.stop()
        eventsMonitoringJobs.remove(walletId)?.let {
            it.monitorHeadlessPaymentsJob?.cancel()
            it.monitorFcmTokenJob.cancel()
            it.monitorNodeEventsJob.cancel()
            it.monitorInFlightPaymentsJob.cancel()
        }
        phoenixGlobal.currencyManager.stopMonitoringForWallet(walletId.nodeIdHash)
    }

    fun refreshFcmToken() {
//       TODO: FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
//            if (!task.isSuccessful) {
//                fr.acinq.phoenix.android.BusinessManager.log.warn("fetching FCM registration token failed: ${task.exception?.localizedMessage}")
//                return@OnCompleteListener
//            }
//            task.result?.let { fr.acinq.phoenix.android.BusinessManager.scope.launch { application.globalPrefs.saveFcmToken(it) } }
//        })
    }

    private suspend fun monitorFcmToken(globalPrefs: GlobalPrefs,business: PhoenixBusiness) {
        val token = globalPrefs.getFcmToken.filterNotNull().first()
        business.connectionsManager.connections.first { it.peer == Connection.ESTABLISHED }
        delay(5000)
        log.i("registering fcm token=$token")
        business.registerFcmToken(token)
    }

    private suspend fun monitorNodeEvents(walletId: WalletId, peerManager: PeerManager, nodeParamsManager: NodeParamsManager, globalPrefs: GlobalPrefs, internalPrefs: InternalPrefs) {
        val monitoringStartedAt = currentTimestampMillis()
        combine(
            peerManager.swapInNextTimeout,
            nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents
        ) { nextTimeout, nodeEvent ->
            nextTimeout to nodeEvent
        }.collect { (_, event) ->
            // TODO: click on notif must deeplink to the notification screen
            when (event) {
                is LiquidityEvents.Rejected -> {
                    log.d("processing liquidity_event=$event")
                    if (event.source == LiquidityEvents.Source.OnChainWallet) {
                        // Check the last time a rejected on-chain swap notification has been shown. If recent, we do not want to trigger a notification every time.
                        val lastRejectedSwap = internalPrefs.getLastRejectedOnchainSwap.first().takeIf {
                            // However, if the app started < 2 min ago, we always want to display a notification. So we'll ignore this check ^
                            currentTimestampMillis() - monitoringStartedAt >= 2 * DateUtils.MINUTE_IN_MILLIS
                        }
                        if (lastRejectedSwap != null
                            && lastRejectedSwap.first == event.amount
                            && currentTimestampMillis() - lastRejectedSwap.second <= 2 * DateUtils.HOUR_IN_MILLIS
                        ) {
                            log.d("ignore this liquidity event as a similar notification was recently displayed")
                            return@collect
                        } else {
                            internalPrefs.saveLastRejectedOnchainSwap(event)
                        }
                    }
                    globalPrefs.getAvailableWalletsMeta.first().getByWalletIdOrDefault(walletId)
                    when (val reason = event.reason) {
                        is LiquidityEvents.Rejected.Reason.PolicySetToDisabled -> {
                            // TODO: SystemNotificationHelper.notifyPaymentRejectedPolicyDisabled(appContext, walletId, walletMetadata, event.source, event.amount, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverAbsoluteFee -> {
                            // TODO: SSystemNotificationHelper.notifyPaymentRejectedOverAbsolute(appContext, walletId, walletMetadata, event.source, event.amount, event.fee, reason.maxAbsoluteFee, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.TooExpensive.OverRelativeFee -> {
                            // TODO: SSystemNotificationHelper.notifyPaymentRejectedOverRelative(appContext, walletId, walletMetadata, event.source, event.amount, event.fee, reason.maxRelativeFeeBasisPoints, nextTimeout?.second)
                        }
                        is LiquidityEvents.Rejected.Reason.MissingOffChainAmountTooLow -> {
                            // TODO: SSystemNotificationHelper.notifyPaymentRejectedAmountTooLow(appContext, walletId, walletMetadata, event.source, event.amount)
                        }
                        // Temporary errors
                        is LiquidityEvents.Rejected.Reason.ChannelFundingInProgress,
                        is LiquidityEvents.Rejected.Reason.NoMatchingFundingRate,
                        is LiquidityEvents.Rejected.Reason.TooManyParts -> {
                            // TODO: SSystemNotificationHelper.notifyPaymentRejectedFundingError(appContext, walletId, walletMetadata, event.source, event.amount)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorPaymentsWhenHeadless(walletId: WalletId, walletMetadata: UserWalletMetadata, nodeParamsManager: NodeParamsManager, currencyManager: CurrencyManager, userPrefs: UserPrefs) {
        nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents.collect { event ->
            when (event) {
                is PaymentEvents.PaymentReceived -> {
//                   TODO: SystemNotificationHelper.notifyPaymentsReceived(
//                        context = appContext,
//                        userPrefs = userPrefs,
//                        walletId = walletId,
//                        userWalletMetadata = walletMetadata,
//                        paymentId = event.payment.id,
//                        paymentAmount = event.payment.amountReceived,
//                        rates = currencyManager.ratesFlow.value,
//                    )
                }
                else -> Unit
            }
        }
    }

    private suspend fun monitorInFlightPayments(peerManager: PeerManager, internalPrefs: InternalPrefs) {
        peerManager.channelsFlow.filterNotNull().collect {
            val inFlightPaymentsCount = it.inFlightPaymentsCount()
            internalPrefs.saveInFlightPaymentsCount(inFlightPaymentsCount)
            // TODO: InflightPaymentsWatcher
//            if (inFlightPaymentsCount == 0) {
//                InflightPaymentsWatcher.cancel(appContext)
//            } else {
//                InflightPaymentsWatcher.scheduleOnce(appContext, delay = 2.hours)
//            }
        }
    }

    fun clear() {
        supervisor.cancel()
    }
}