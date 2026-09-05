// Copied verbatim from `androidHostTest`, which despite its name contains no android API:
// it is ktor, javax.net.ssl and lightning-kmp's own JvmTcpSocket, all of which this target
// has. The duplication is deliberate. `ElectrumServersTest` is @Ignore'd on every platform
// -- it is a manual check of the preset server list -- so the actual only has to exist for
// the module to compile, and ios satisfies that with an empty body. Doing the same here
// would make `connect_to_mainnet_servers` pass without connecting to anything the moment
// somebody removes the @Ignore, which is a worse outcome than a duplicated file.
//
// The tidier fix is a shared source set that `androidHostTest` and `jvmTest` both depend
// on; that is a change to how this module is wired, not to what it does, so it is left out
// of the change that introduced the jvm target.

package fr.acinq.phoenix.data

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.NoTagFormatter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import fr.acinq.lightning.io.JvmTcpSocket
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.ServerAddress
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.tls
import kotlinx.coroutines.Dispatchers
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


private object ElectrumServersTestHelper {
    val selectorManager = ActorSelectorManager(Dispatchers.IO)
    val loggerFactory = LoggerFactory(loggerConfigInit(
        logWriters = arrayOf(CommonWriter(NoTagFormatter)),
        minSeverity = Severity.Info
    ))
}

actual suspend fun connect(server: ServerAddress) {
    val socket = aSocket(ElectrumServersTestHelper.selectorManager).tcp().connect(server.host, server.port).let { socket ->
        when (val tls = server.tls) {
            is TcpSocket.TLS.TRUSTED_CERTIFICATES -> socket.tls(Dispatchers.IO)
            is TcpSocket.TLS.PINNED_PUBLIC_KEY -> socket.tls(
                coroutineContext = Dispatchers.IO,
                config = TLSConfigBuilder().apply {
                    val expectedPubkey = tls.pubKey
                    val logger = ElectrumServersTestHelper.loggerFactory.newLogger(this::class)

                    // build a default X509 trust manager.
                    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())!!
                    factory.init(null as KeyStore?)
                    val defaultX509TrustManager = factory.trustManagers!!.filterIsInstance<X509TrustManager>().first()

                    // create a new trust manager that always accepts certificates for the pinned public key, or falls back to standard procedure.
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            defaultX509TrustManager.checkClientTrusted(chain, authType)
                        }

                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                            val serverKey = JvmTcpSocket.buildPublicKey(chain?.asList()?.firstOrNull()?.publicKey?.encoded ?: throw CertificateException("certificate missing"), logger)
                            val pinnedKey = JvmTcpSocket.buildPublicKey(Base64.getDecoder().decode(expectedPubkey), logger)

                            if (serverKey != pinnedKey) {
                                throw BadCertificate(expectedPubkey, actualPubkey = Base64.getEncoder().encodeToString(serverKey.encoded))
                            }
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> = defaultX509TrustManager.acceptedIssuers
                    }
                }.build()
            )
            else -> throw IllegalArgumentException("reject connection to server with tls=$tls")
        }
    }
    socket.close()
}