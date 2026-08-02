package fr.acinq.phoenix.managers.global

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

actual class NetworkMonitor actual constructor(loggerFactory: LoggerFactory, val ctx: PlatformContext) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {

    val logger = loggerFactory.newLogger(this::class)

    private val isCallbackRegistered = AtomicBoolean(false)

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            logger.debug { "network is now $network" }
            _networkState.value = NetworkState.Available
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            super.onBlockedStatusChanged(network, blocked)
            logger.debug { "block status change to $blocked for network=$network"}
        }

        override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
            logger.debug { "default network changed capabilities to $networkCapabilities" }
        }

        override fun onLinkPropertiesChanged(network : Network, linkProperties : LinkProperties) {
            logger.debug { "default network changed link properties to $linkProperties" }
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            super.onLosing(network, maxMsToLive)
            logger.debug { "losing network in ${maxMsToLive}ms..." }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            logger.info { "network has been lost" }
            _networkState.value = NetworkState.NotAvailable
        }

        override fun onUnavailable() {
            super.onUnavailable()
            logger.info { "network is unavailable" }
            _networkState.value = NetworkState.NotAvailable
        }
    }

    actual fun enable() {
    }

    actual fun disable() {
    }

    actual fun start() {
        val connectivityManager = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (isCallbackRegistered.compareAndSet(false, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                // TODO: Implement logic to register connectivity monitor
            }
        }
    }

    actual fun stop() {
        val connectivityManager = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
        isCallbackRegistered.set(false)
    }
}
