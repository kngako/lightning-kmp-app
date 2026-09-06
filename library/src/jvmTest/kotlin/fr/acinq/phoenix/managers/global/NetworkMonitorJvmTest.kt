package fr.acinq.phoenix.managers.global

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.NoTagFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AppConnectionsDaemon` gates the peer, electrum and tor connections on `networkState`,
 * so the lifecycle here decides whether a desktop wallet reconnects at all. None of it is
 * checked by the compiler: `start`/`stop` return Unit and the flow always has *some*
 * value, so every way of getting this wrong still builds and still looks alive.
 *
 * The polling itself is not asserted against a fixed answer -- whether this host has a
 * link is not something a test gets to decide. Instead the expectation is computed the
 * same way the actual computes it, so the assertion is "the monitor agrees with the host"
 * rather than "the host is online", and holds in a container with no interfaces up.
 */
class NetworkMonitorJvmTest {

    private val tempDir: File = Files.createTempDirectory("phoenix-networkmonitor-test").toFile()
    private val monitors = mutableListOf<NetworkMonitor>()

    @AfterTest
    fun cleanup() {
        // `stop` cancels the poll job but deliberately leaves the supervisor alive, so the
        // scope has to be torn down separately or every test leaks a live parent job.
        monitors.forEach { (it as CoroutineScope).cancel() }
        tempDir.deleteRecursively()
    }

    private fun monitor(): NetworkMonitor = NetworkMonitor(
        loggerFactory = LoggerFactory(
            loggerConfigInit(
                logWriters = arrayOf(CommonWriter(NoTagFormatter)),
                minSeverity = Severity.Info,
            )
        ),
        ctx = PlatformContext(applicationDir = tempDir),
    ).also { monitors += it }

    /** The actual's own `hasUsableLink`, which is private. */
    private fun hostHasUsableLink(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.any { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
            ?: false
    } catch (e: SocketException) {
        false
    }

    private fun expectedState(): NetworkState =
        if (hostHasUsableLink()) NetworkState.Available else NetworkState.NotAvailable

    private fun NetworkMonitor.activePollJobs(): Int =
        (this as CoroutineScope).coroutineContext[Job]?.children?.count { it.isActive } ?: 0

    private fun NetworkMonitor.awaitState(state: NetworkState) = runBlocking {
        withTimeout(AWAIT_TIMEOUT_MS) { networkState.first { it == state } }
    }

    /**
     * The daemon reads the flow before anything starts polling, and a false `Available`
     * there would have it believe a connection is possible before the link was ever
     * looked at.
     */
    @Test
    fun `a monitor reports NotAvailable before it is started`() {
        assertEquals(NetworkState.NotAvailable, monitor().networkState.value)
    }

    @Test
    fun `starting reports the link state of this host`() {
        val monitor = monitor()
        monitor.start()

        monitor.awaitState(expectedState())
    }

    /**
     * The compareAndSet guard in `start`. Without it a second call launches a second loop
     * and overwrites `pollJob`, orphaning the first -- which then keeps writing to the
     * flow forever, including after `stop`. Counted rather than waited on, because the
     * orphan would only give itself away one poll interval later.
     */
    @Test
    fun `starting twice does not leave a second poll loop behind`() {
        val monitor = monitor()

        monitor.start()
        assertEquals(1, monitor.activePollJobs(), "expected exactly one poll loop after start")

        monitor.start()
        assertEquals(1, monitor.activePollJobs(), "the second start launched another poll loop")
    }

    @Test
    fun `stopping cancels the poll loop and resets the state`() {
        val monitor = monitor()
        monitor.start()
        monitor.awaitState(expectedState())

        monitor.stop()

        assertEquals(NetworkState.NotAvailable, monitor.networkState.value)
        assertEquals(0, monitor.activePollJobs(), "the poll loop outlived stop")
    }

    /**
     * `stop` resets `isPolling`, so a monitor is restartable. The daemon does exactly this
     * across a disconnect, and a monitor that came back with the guard still latched would
     * report NotAvailable for the rest of the process -- nothing would ever reconnect.
     */
    @Test
    fun `a monitor can be started again after being stopped`() {
        val monitor = monitor()
        monitor.start()
        monitor.awaitState(expectedState())
        monitor.stop()
        assertEquals(NetworkState.NotAvailable, monitor.networkState.value)

        monitor.start()

        monitor.awaitState(expectedState())
        assertEquals(1, monitor.activePollJobs(), "restart did not launch a poll loop")
    }

    @Test
    fun `stopping a monitor that was never started is a no-op`() {
        val monitor = monitor()

        monitor.stop()

        assertEquals(NetworkState.NotAvailable, monitor.networkState.value)
        assertEquals(0, monitor.activePollJobs())
    }

    /**
     * Ios uses these to gate `start`; this actual documents them as no-ops. Nothing in
     * common code calls them, so if they ever became load-bearing here the desktop wallet
     * would go quiet with no caller to blame.
     */
    @Test
    fun `enable and disable do not gate polling`() {
        val monitor = monitor()

        monitor.disable()
        monitor.start()

        monitor.awaitState(expectedState())
        assertTrue(monitor.activePollJobs() > 0, "disable() suppressed the poll loop")

        monitor.enable()
        assertEquals(1, monitor.activePollJobs(), "enable() started a second poll loop")
    }

    private companion object {
        /** Generous: the first poll runs immediately, so this only ever covers scheduling. */
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
