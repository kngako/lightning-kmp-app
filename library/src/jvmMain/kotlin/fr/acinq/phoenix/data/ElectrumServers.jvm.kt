package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress

/**
 * Not a copy of the android actual, which uses 10.0.2.2 -- the emulator's alias for the
 * host machine's loopback. A jvm process is already on the host, so it wants the real
 * thing.
 */
actual fun platformElectrumRegtestConf(): ServerAddress =
    ServerAddress(host = "127.0.0.1", port = 51002, tls = TcpSocket.TLS.DISABLED)
