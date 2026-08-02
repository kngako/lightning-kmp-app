package fr.acinq.phoenix.utils.extensions

import fr.acinq.phoenix.data.DecryptSeedResult

actual inline fun gracefulSingleSeedDecryption(action: () -> DecryptSeedResult): DecryptSeedResult = try {
    action.invoke()
} catch (e: Throwable) {
    return DecryptSeedResult.Failure.DecryptionError(e)
}

actual inline fun gracefulMultiSeedDecryption(action: () -> DecryptSeedResult): DecryptSeedResult = try {
    action.invoke()
} catch (e: Exception) {
    return DecryptSeedResult.Failure.DecryptionError(e)
}