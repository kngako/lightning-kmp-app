package fr.acinq.phoenix.security

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.CoreCrypto.CCAlgorithm
import platform.CoreCrypto.CCCryptorCreateWithMode
import platform.CoreCrypto.CCCryptorFinal
import platform.CoreCrypto.CCCryptorGetOutputLength
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorStatus
import platform.CoreCrypto.CCCryptorUpdate
import platform.CoreCrypto.CCMode
import platform.CoreCrypto.CCOperation
import platform.CoreCrypto.CCPadding
import platform.CoreCrypto.ccPKCS7Padding
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCAlignmentError
import platform.CoreCrypto.kCCBufferTooSmall
import platform.CoreCrypto.kCCDecodeError
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCMemoryFailure
import platform.CoreCrypto.kCCModeCBC
import platform.CoreCrypto.kCCParamError
import platform.CoreCrypto.kCCSuccess
import platform.CoreCrypto.kCCUnimplemented
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
private val almostEmptyArrayPinned = ByteArray(1).pin()

@ExperimentalForeignApi
fun ByteArray.safeRefTo(index: Int): CValuesRef<ByteVar> {
    if (index == size) return almostEmptyArrayPinned.addressOf(0)
    return refTo(index)
}

/**
 * Might just load up cryptography-kotlin as a dependency as it would allow us to have this logic in commonMain
 * https://github.com/whyoleg/cryptography-kotlin.git
 */
@OptIn(ExperimentalForeignApi::class)
class CCCipher(
    private val algorithm: CCAlgorithm,
    private val mode: CCMode,
    private val padding: CCPadding,
    private val key: ByteArray,
)  {
    companion object {
        fun phoenixCipherForKey(key: ByteArray): CCCipher {
            return CCCipher(
                algorithm =  kCCAlgorithmAES,
                mode = kCCModeCBC,
                padding = ccPKCS7Padding,
                key = key,
            )
        }
    }
    fun encrypt(iv: ByteArray?, plaintext: ByteArray): ByteArray = memScoped {
        useCryptor { cryptorRef ->
            cryptorRef.create(kCCEncrypt, iv?.refTo(0))
            val ciphertextOutput = ByteArray(cryptorRef.outputLength(plaintext.size))

            val dataOutMoved = alloc<size_tVar>()
            val moved = cryptorRef.update(
                dataIn = plaintext.safeRefTo(0),
                dataInLength = plaintext.size,
                dataOut = ciphertextOutput.safeRefTo(0),
                dataOutAvailable = ciphertextOutput.size,
                dataOutMoved = dataOutMoved,
            )

            if (ciphertextOutput.size != moved) cryptorRef.final(
                dataOut = ciphertextOutput.refTo(moved),
                dataOutAvailable = ciphertextOutput.size - moved,
                dataOutMoved = dataOutMoved,
            )
            ciphertextOutput
        }
    }

    fun decrypt(iv: ByteArray?, ciphertext: ByteArray, ciphertextStartIndex: Int): ByteArray = memScoped {
        useCryptor { cryptorRef ->
            cryptorRef.create(kCCDecrypt, iv?.refTo(0))

            val plaintextOutput = ByteArray(cryptorRef.outputLength(ciphertext.size - ciphertextStartIndex))

            val dataOutMoved = alloc<size_tVar>()
            var moved = cryptorRef.update(
                dataIn = ciphertext.safeRefTo(ciphertextStartIndex),
                dataInLength = ciphertext.size - ciphertextStartIndex,
                dataOut = plaintextOutput.safeRefTo(0),
                dataOutAvailable = plaintextOutput.size,
                dataOutMoved = dataOutMoved
            )

            if (plaintextOutput.size != moved) moved += cryptorRef.final(
                dataOut = plaintextOutput.refTo(moved),
                dataOutAvailable = plaintextOutput.size - moved,
                dataOutMoved = dataOutMoved
            )

            if (plaintextOutput.size == moved) {
                plaintextOutput
            } else {
                plaintextOutput.copyOf(moved)
            }
        }
    }

    private inline fun <T> MemScope.useCryptor(block: (cryptorRef: CCCryptorRefVar) -> T): T {
        val cryptorRef = alloc<CCCryptorRefVar>()
        try {
            return block(cryptorRef)
        } finally {
            CCCryptorRelease(cryptorRef.value)
        }
    }

    private fun CCCryptorRefVar.create(op: CCOperation, iv: CValuesRef<*>?) {
        checkResult(
            CCCryptorCreateWithMode(
                op = op,
                cryptorRef = ptr,
                alg = algorithm,
                mode = mode,
                padding = padding,
                key = key.refTo(0),
                keyLength = key.size.convert(),
                iv = iv,

                // unused options
                options = 0.convert(),
                tweak = null,
                tweakLength = 0.convert(),
                numRounds = 0,
            )
        )
    }

    private fun CCCryptorRefVar.outputLength(inputLength: Int): Int {
        return CCCryptorGetOutputLength(
            cryptorRef = value,
            inputLength = inputLength.convert(),
            final = true
        ).convert()
    }

    private fun CCCryptorRefVar.update(
        dataIn: CValuesRef<*>,
        dataInLength: Int,
        dataOut: CValuesRef<*>,
        dataOutAvailable: Int,
        dataOutMoved: size_tVar,
    ): Int {
        checkResult(
            CCCryptorUpdate(
                cryptorRef = value,
                dataIn = dataIn,
                dataInLength = dataInLength.convert(),
                dataOut = dataOut,
                dataOutAvailable = dataOutAvailable.convert(),
                dataOutMoved = dataOutMoved.ptr
            )
        )
        return dataOutMoved.value.convert()
    }

    private fun CCCryptorRefVar.final(
        dataOut: CValuesRef<*>,
        dataOutAvailable: Int,
        dataOutMoved: size_tVar,
    ): Int {
        checkResult(
            CCCryptorFinal(
                cryptorRef = value,
                dataOut = dataOut,
                dataOutAvailable = dataOutAvailable.convert(),
                dataOutMoved = dataOutMoved.ptr
            )
        )
        return dataOutMoved.value.convert()
    }
}

private fun checkResult(result: CCCryptorStatus) {
    error(
        when (result) {
            kCCSuccess        -> return
            kCCParamError     -> "Illegal parameter value."
            kCCBufferTooSmall -> "Insufficient buffer provided for specified operation."
            kCCMemoryFailure  -> "Memory allocation failure."
            kCCAlignmentError -> "Input size was not aligned properly."
            kCCDecodeError    -> "Input data did not decode or decrypt properly."
            kCCUnimplemented  -> "Function not implemented for the current algorithm."
            else              -> "CCCrypt failed with code $result"
        }
    )
}