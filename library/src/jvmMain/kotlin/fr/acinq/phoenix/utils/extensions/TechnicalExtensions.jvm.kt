package fr.acinq.phoenix.utils.extensions

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.data.DecryptSeedResult
import kotlinx.serialization.SerializationException
import java.security.KeyStoreException

// Identical to the android actuals. `java.security.KeyStoreException` is a plain JCA type
// and exists here too, so the branching carries over unchanged -- whatever backs
// keyStoreEncryption/keyStoreDecryption on this platform, failing to open the key store is
// still the case that has to be told apart from a corrupt payload.

actual inline fun gracefulSingleSeedDecryption(action: () -> DecryptSeedResult): DecryptSeedResult = try {
    action.invoke()
} catch (e: Exception) {
    return when (e) {
        is KeyStoreException -> DecryptSeedResult.Failure.KeyStoreFailure(e)
        else -> DecryptSeedResult.Failure.DecryptionError(e)
    }
}

actual inline fun gracefulMultiSeedDecryption(action: () -> DecryptSeedResult): DecryptSeedResult = try {
    action.invoke()
} catch (e: Exception) {
    val log = Logger.withTag("gracefulMultiSeedDecryption")
    return when (e) {
        is SerializationException, is IllegalArgumentException -> {
            log.e("failed to decrypt [V2.MultipleSeed]: ${e.javaClass.simpleName}")
            DecryptSeedResult.Failure.SerializationError
        }
        is KeyStoreException -> {
            log.e("failed to decrypt [V2.MultipleSeed]: ", e)
            DecryptSeedResult.Failure.KeyStoreFailure(e)
        }
        else -> {
            log.e("failed to decrypt [V2.MultipleSeed]: ", e)
            DecryptSeedResult.Failure.DecryptionError(e)
        }
    }
}
