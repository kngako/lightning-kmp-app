package fr.acinq.phoenix.security

import fr.acinq.bitcoin.PrivateKey
import fr.acinq.phoenix.managers.nostrPublicKeyHex
import kotlinx.serialization.json.Json
import okio.Buffer

/**
 * Bare nostr secrets, encrypted at rest under the same keystore key as the seed.
 *
 * A sibling of [EncryptedSeed], not a variant of it. `seed.dat` holds mnemonics --
 * its reader runs `MnemonicCode.toSeed` over every entry and builds a `LocalKeyManager`
 * from the result -- and a 32-byte key has no place in that shape. Nor could it be
 * given one without forking a file format this library inherits from upstream and
 * making every older build report the *wallet* unreadable. So this is its own file
 * with its own version byte; see docs/nsec-sign-in.md in the consuming app.
 *
 * The plaintext is UTF-8 JSON, `{ "<x-only public key hex>": "<private key hex>" }`.
 * Keyed by the public key so that a lookup and a duplicate check are the same map
 * operation, and so a corrupt entry can be told from a valid one on read: every key
 * is re-derived on decryption and has to land on its own map key.
 *
 * Layout, which is a compatibility contract and is pinned by `EncryptedNostrKeysTest`:
 *
 * ```
 * byte 0        VERSION = 1
 * bytes 1..16   iv
 * bytes 17..    ciphertext
 * ```
 *
 * One version byte, not two. [EncryptedSeed.V2] carries a second because it has to
 * distinguish a single seed from several; this file has one shape, and a new shape
 * would be a new version.
 */
class EncryptedNostrKeys(val iv: ByteArray, val ciphertext: ByteArray) {

    /**
     * Decrypts the payload and returns the keys by x-only public key, hex.
     *
     * Throws `IllegalArgumentException` when an entry's private key does not derive the
     * public key it is filed under -- the file is this library's own and that can only
     * mean corruption, and a key filed under the wrong identity must not be handed out.
     */
    fun decryptAndGetKeyMap(): Map<String, PrivateKey> {
        val payload = keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext).decodeToString()
        val json = Json.decodeFromString<Map<String, String>>(payload)
        return json.map { (publicKeyHex, privateKeyHex) ->
            val privateKey = PrivateKey.fromHex(privateKeyHex)
            require(privateKey.isValid()) { "stored key is not a valid secp256k1 secret" }
            require(privateKey.nostrPublicKeyHex() == publicKeyHex) {
                "stored key does not derive the public key it is filed under"
            }
            publicKeyHex to privateKey
        }.toMap()
    }

    fun serialize(): ByteArray {
        if (iv.size != IV_LENGTH) {
            throw RuntimeException("cannot serialize nostr keys: iv not of the correct length")
        }
        val array = Buffer()
        array.writeByte(VERSION.toInt())
        array.write(iv)
        array.write(ciphertext)
        return array.readByteArray()
    }

    override fun toString(): String = "EncryptedNostrKeys"

    companion object {
        const val VERSION: Byte = 1
        private const val IV_LENGTH = 16

        /** Reads a serialized file back. Throws `IllegalArgumentException` on an unknown version. */
        fun deserialize(serialized: ByteArray): EncryptedNostrKeys {
            val stream = Buffer().write(serialized)
            val version = stream.readByte()
            require(version == VERSION) { "unhandled nostr keys file version=$version" }
            require(stream.size >= IV_LENGTH) { "nostr keys file is truncated" }
            val iv = stream.readByteArray(IV_LENGTH.toLong())
            val ciphertext = stream.readByteArray()
            return EncryptedNostrKeys(iv, ciphertext)
        }

        /** Encrypts [keys], keyed by x-only public key hex, under [KeyStoreNames.KEY_NO_AUTH]. */
        fun encrypt(keys: Map<String, PrivateKey>): EncryptedNostrKeys {
            val json = Json.encodeToString(
                keys.map { (publicKeyHex, privateKey) -> publicKeyHex to privateKey.value.toHex() }.toMap()
            )
            val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, json.encodeToByteArray())
            return EncryptedNostrKeys(iv, ciphertext)
        }
    }
}
