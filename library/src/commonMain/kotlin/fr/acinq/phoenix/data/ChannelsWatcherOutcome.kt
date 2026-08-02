package fr.acinq.phoenix.data

import kotlinx.serialization.Serializable

@Serializable
sealed class ChannelsWatcherOutcome {
    abstract val timestamp: Long

    @Serializable
    data class Unknown(override val timestamp: Long) : ChannelsWatcherOutcome()

    @Serializable
    data class Nominal(override val timestamp: Long) : ChannelsWatcherOutcome()

    @Serializable
    data class RevokedFound(override val timestamp: Long) : ChannelsWatcherOutcome()
}