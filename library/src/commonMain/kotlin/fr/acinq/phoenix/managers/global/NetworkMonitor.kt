package fr.acinq.phoenix.managers.global

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.flow.StateFlow

enum class NetworkState {
    Available,
    NotAvailable
}

expect class NetworkMonitor(loggerFactory: LoggerFactory, ctx: PlatformContext) {
    val networkState: StateFlow<NetworkState>
    fun enable()
    fun disable()
    fun start()
    fun stop()
}
