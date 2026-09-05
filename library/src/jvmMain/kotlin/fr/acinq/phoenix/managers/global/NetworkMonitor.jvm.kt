package fr.acinq.phoenix.managers.global

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Android gets pushed connectivity changes from `ConnectivityManager.NetworkCallback` and
 * ios from `nw_path_monitor`. The jvm has no equivalent, so this polls.
 *
 * **This is weaker than either, deliberately.** It reports on the *link*: whether any
 * non-loopback interface is up and carries an address. That catches the case a desktop
 * actually hits -- the lid closes, wifi drops, a cable is pulled -- and does not catch a
 * captive portal or an interface that is up with no route to anywhere. The alternative,
 * probing a remote host on a timer, trades that for false negatives behind egress
 * firewalls and an unprompted outbound connection every few seconds, which is a poor deal
 * for a wallet.
 *
 * `AppConnectionsDaemon` gates the peer, electrum and tor connections on this flow, so the
 * failure that matters is a false `NotAvailable` -- nothing would reconnect. A false
 * `Available` is comparatively benign: the lightning stack's own retry logic handles a
 * link that is up but going nowhere.
 */
actual class NetworkMonitor actual constructor(
    loggerFactory: LoggerFactory,
    ctx: PlatformContext,
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {

    private val logger = loggerFactory.newLogger(this::class)

    private val _networkState = MutableStateFlow(NetworkState.NotAvailable)
    actual val networkState: StateFlow<NetworkState> = _networkState

    private val isPolling = AtomicBoolean(false)
    private val pollJob = AtomicReference<Job?>(null)

    /** No-ops, as on android. Ios uses these to gate `start`; nothing in common code calls them. */
    actual fun enable() {}

    actual fun disable() {}

    actual fun start() {
        // Mirrors android's compareAndSet guard: `start` is called from a daemon coroutine
        // and must not leave a second poll loop running behind the first.
        if (!isPolling.compareAndSet(false, true)) return
        pollJob.set(
            launch {
                while (isActive) {
                    val state = if (hasUsableLink()) NetworkState.Available else NetworkState.NotAvailable
                    if (state != _networkState.value) {
                        logger.info { "network is now $state" }
                        _networkState.value = state
                    }
                    delay(POLL_INTERVAL)
                }
            }
        )
    }

    actual fun stop() {
        if (!isPolling.compareAndSet(true, false)) return
        pollJob.getAndSet(null)?.cancel()
        _networkState.value = NetworkState.NotAvailable
    }

    private fun hasUsableLink(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
            ?: false
    } catch (e: SocketException) {
        // Enumeration can fail outright under some security managers and container setups.
        // Reporting "no link" here would stall every connection, so say nothing changed and
        // let the next tick try again.
        logger.debug { "could not enumerate network interfaces: ${e.message}" }
        _networkState.value == NetworkState.Available
    }

    private companion object {
        /**
         * Interface enumeration is a cheap syscall, so this is bounded by how stale a
         * desktop user will tolerate the connection indicator being, not by cost.
         */
        val POLL_INTERVAL = 10.seconds
    }
}
