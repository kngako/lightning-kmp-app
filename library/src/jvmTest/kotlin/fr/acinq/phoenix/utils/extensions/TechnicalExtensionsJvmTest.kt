package fr.acinq.phoenix.utils.extensions

import fr.acinq.phoenix.data.DecryptSeedResult
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.security.KeyStoreException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Pure exception mapping, so nothing here is checked by anything but a test. What it
 * decides is what the caller shows the user: a key store that will not open is a
 * "try again / your os is holding the key" case, while a decryption error on a payload
 * that did open is closer to "this seed file is damaged". Getting the two the wrong way
 * round sends someone down the wrong recovery path with their wallet on the line.
 *
 * The single- and multi-seed actuals do not classify the same exception the same way. That
 * is pinned below rather than smoothed over, so that unifying them later has to be a
 * decision somebody makes on purpose.
 */
class TechnicalExtensionsJvmTest {

    private val success = DecryptSeedResult.Success(userWalletsMap = emptyMap())

    @Test
    fun `single - a success is passed through untouched`() {
        assertSame(success, gracefulSingleSeedDecryption { success })
    }

    @Test
    fun `single - a key store failure is told apart from a corrupt payload`() {
        val cause = KeyStoreException("key store is not open")
        val result = gracefulSingleSeedDecryption { throw cause }

        val failure = assertIs<DecryptSeedResult.Failure.KeyStoreFailure>(result)
        assertSame(cause, failure.cause, "the original exception should survive for the caller to log")
    }

    @Test
    fun `single - anything else is a decryption error that keeps its cause`() {
        val cause = IOException("truncated seed file")
        val result = gracefulSingleSeedDecryption { throw cause }

        val failure = assertIs<DecryptSeedResult.Failure.DecryptionError>(result)
        assertSame(cause, failure.cause)
    }

    /**
     * The divergence from [gracefulMultiSeedDecryption]: there is no SerializationError
     * branch on this path, so a deserialization failure arrives as a DecryptionError.
     */
    @Test
    fun `single - a serialization failure is a decryption error, unlike the multi-seed path`() {
        val result = gracefulSingleSeedDecryption { throw SerializationException("unexpected json") }

        assertIs<DecryptSeedResult.Failure.DecryptionError>(result)
    }

    @Test
    fun `multi - a success is passed through untouched`() {
        assertSame(success, gracefulMultiSeedDecryption { success })
    }

    @Test
    fun `multi - a serialization failure is reported as such`() {
        val result = gracefulMultiSeedDecryption { throw SerializationException("unexpected json") }

        assertIs<DecryptSeedResult.Failure.SerializationError>(result)
    }

    /**
     * `SerializationException` extends `IllegalArgumentException`, so the two `when`
     * branches overlap. A bare IllegalArgumentException -- what `Json` throws for a
     * well-formed document of the wrong shape -- has to reach the same answer.
     */
    @Test
    fun `multi - a bare illegal argument is also a serialization error`() {
        val result = gracefulMultiSeedDecryption { throw IllegalArgumentException("no such wallet id") }

        assertIs<DecryptSeedResult.Failure.SerializationError>(result)
    }

    @Test
    fun `multi - a key store failure keeps its cause`() {
        val cause = KeyStoreException("key store is not open")
        val result = gracefulMultiSeedDecryption { throw cause }

        val failure = assertIs<DecryptSeedResult.Failure.KeyStoreFailure>(result)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `multi - anything else is a decryption error that keeps its cause`() {
        val cause = IOException("truncated seed file")
        val result = gracefulMultiSeedDecryption { throw cause }

        val failure = assertIs<DecryptSeedResult.Failure.DecryptionError>(result)
        assertSame(cause, failure.cause)
    }
}
