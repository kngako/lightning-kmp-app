package fr.acinq.phoenix.security

import fr.acinq.phoenix.data.WalletId
import kotlinx.serialization.json.Json
import okio.Buffer

/**
 * Recovery codes, encrypted at rest under the same keystore key as the seed.
 *
 * A recovery code backs a profile whose 24 words are in `seed.dat` like any phrase's; this
 * file holds only the code, because the words cannot give it back and the consuming app
 * shows it again. It is a file of its own rather than an entry in `seed.dat` so that an
 * older build, which does not look for it, reads `seed.dat` exactly as before and lists the
 * profile as the phrase it also is. See docs/recovery-code-v3.md in the consuming app.
 *
 * The plaintext is UTF-8 JSON, profile id to code:
 *
 * ```
 * { "<hash160 hex>": "LBZC-MYTK-E9P3-7C7B-AHJN-KX79-22CY-WAY7-K7QZ" }
 * ```
 *
 * The library stores strings. It does not know the code's format; the app parses each one
 * on read and checks it derives the words beside it.
 *
 * Layout, which is a compatibility contract and is pinned by `EncryptedRecoveryCodesTest`:
 *
 * ```
 * byte 0        VERSION = 1
 * bytes 1..16   iv
 * bytes 17..    ciphertext
 * ```
 */
class EncryptedRecoveryCodes(val iv: ByteArray, val ciphertext: ByteArray) {

    /**
     * Decrypts the payload. Throws `IllegalArgumentException` on an entry not filed under a
     * hash160, or a code that is empty -- the file is this library's own, and either can
     * only mean corruption.
     */
    fun decryptAndGetCodes(): Map<WalletId, String> {
        val payload = keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext).decodeToString()
        val entries = Json.decodeFromString<Map<String, String>>(payload)
        return entries.map { (id, code) ->
            require(id.length == 40 && id.all { it in '0'..'9' || it in 'a'..'f' }) {
                "stored recovery code is not filed under a profile id"
            }
            require(code.isNotBlank()) { "stored recovery code is empty" }
            WalletId(id) to code
        }.toMap()
    }

    fun serialize(): ByteArray {
        if (iv.size != IV_LENGTH) {
            throw RuntimeException("cannot serialize recovery codes: iv not of the correct length")
        }
        val array = Buffer()
        array.writeByte(VERSION.toInt())
        array.write(iv)
        array.write(ciphertext)
        return array.readByteArray()
    }

    override fun toString(): String = "EncryptedRecoveryCodes"

    companion object {
        const val VERSION: Byte = 1
        private const val IV_LENGTH = 16

        /** Reads a serialized file back. Throws `IllegalArgumentException` on an unknown version. */
        fun deserialize(serialized: ByteArray): EncryptedRecoveryCodes {
            val stream = Buffer().write(serialized)
            val version = stream.readByte()
            require(version == VERSION) { "unhandled recovery codes file version=$version" }
            require(stream.size >= IV_LENGTH) { "recovery codes file is truncated" }
            val iv = stream.readByteArray(IV_LENGTH.toLong())
            val ciphertext = stream.readByteArray()
            return EncryptedRecoveryCodes(iv, ciphertext)
        }

        /** Encrypts [codes], by profile id, under [KeyStoreNames.KEY_NO_AUTH]. */
        fun encrypt(codes: Map<WalletId, String>): EncryptedRecoveryCodes {
            val payload = Json.encodeToString(codes.mapKeys { (id, _) -> id.nodeIdHash })
            val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, payload.encodeToByteArray())
            return EncryptedRecoveryCodes(iv, ciphertext)
        }
    }
}
