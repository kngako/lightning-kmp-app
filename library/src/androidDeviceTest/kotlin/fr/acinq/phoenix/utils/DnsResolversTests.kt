package fr.acinq.phoenix.utils

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DnsResolversTests {

    @Test
    fun testDnsResolversBip353() = runBlocking {

        val username = "flashybugle70"
        val domain = "testnet.phoenixwallet.me"

        DnsResolvers.entries.forEach {
            val json = it.getTxtRecord("$username.user._bitcoin-payment.$domain")
            assertTrue { json.isNotEmpty() }
        }
    }
}