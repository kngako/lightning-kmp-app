package fr.acinq.phoenix.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertTrue

/** [AndroidJUnit4] resolves to the on-device runner here and to Robolectric in `androidHostTest`. */
@RunWith(AndroidJUnit4::class)
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