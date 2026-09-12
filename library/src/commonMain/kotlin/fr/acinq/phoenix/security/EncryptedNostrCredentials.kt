package fr.acinq.phoenix.security

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.XonlyPublicKey
import fr.acinq.phoenix.managers.nostrPublicKeyHex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer

/**
 * Nostr credentials, encrypted at rest under the same keystore key as the seed.
 *
 * The successor to [EncryptedNostrKeys]. That file held one shape -- a secret per
 * public key -- and its doc said a new shape would be a new version. This is the new
 * shape: one entry per public key, each saying what the device holds for it, so that
 * a profile signed in to read-only and the same profile with its nsec pasted later are
 * one entry in one file, and the second is a single write over the first. It is a new
 * *file* rather than a new version byte of the old one because an older build's
 * listing aborts on an unreadable version and would list no identities at all, seed
 * wallets included; a file it does not look for cannot do that to it. See
 * docs/npub-sign-in.md in the consuming app.
 *
 * The plaintext is UTF-8 JSON keyed by x-only public key hex:
 *
 * ```
 * {
 *   "<x-only public key hex>": { "type": "secret", "privateKey": "<private key hex>" },
 *   "<x-only public key hex>": { "type": "public" }
 * }
 * ```
 *
 * Keyed by the public key so that a lookup and a duplicate check are the same map
 * operation, and so a corrupt entry can be told from a valid one on read: a secret is
 * re-derived on decryption and has to land on its own map key, and a public entry's
 * map key has to name a point on the curve.
 *
 * Layout, which is a compatibility contract and is pinned by
 * `EncryptedNostrCredentialsTest`:
 *
 * ```
 * byte 0        VERSION = 1
 * bytes 1..16   iv
 * bytes 17..    ciphertext
 * ```
 */
class EncryptedNostrCredentials(val iv: ByteArray, val ciphertext: ByteArray) {

    /**
     * Decrypts the payload and returns the credentials by x-only public key, hex.
     *
     * Throws `IllegalArgumentException` when a secret does not derive the public key it
     * is filed under, or a public entry's key is not a valid x-only key -- the file is
     * this library's own and either can only mean corruption, and a key filed under the
     * wrong identity must not be handed out.
     */
    fun decryptAndGetCredentials(): Map<String, NostrCredential> {
        val payload = keyStoreDecryption(KeyStoreNames.KEY_NO_AUTH, iv, ciphertext).decodeToString()
        val entries = json.decodeFromString<Map<String, Entry>>(payload)
        return entries.map { (publicKeyHex, entry) ->
            require(publicKeyHex.length == 64 && publicKeyHex.all { it in '0'..'9' || it in 'a'..'f' }) {
                "stored credential is not filed under an x-only public key"
            }
            val credential = when (entry) {
                is Entry.Secret -> {
                    val privateKey = PrivateKey.fromHex(entry.privateKey)
                    require(privateKey.isValid()) { "stored key is not a valid secp256k1 secret" }
                    require(privateKey.nostrPublicKeyHex() == publicKeyHex) {
                        "stored key does not derive the public key it is filed under"
                    }
                    NostrCredential.Secret(privateKey)
                }
                is Entry.Public -> {
                    require(XonlyPublicKey(ByteVector32(publicKeyHex)).publicKey.isValid()) {
                        "stored public key is not a point on the curve"
                    }
                    NostrCredential.Public
                }
            }
            publicKeyHex to credential
        }.toMap()
    }

    fun serialize(): ByteArray {
        if (iv.size != IV_LENGTH) {
            throw RuntimeException("cannot serialize nostr credentials: iv not of the correct length")
        }
        val array = Buffer()
        array.writeByte(VERSION.toInt())
        array.write(iv)
        array.write(ciphertext)
        return array.readByteArray()
    }

    override fun toString(): String = "EncryptedNostrCredentials"

    /**
     * The wire shape of one entry. Private to the file: the domain type is
     * [NostrCredential], which carries a [PrivateKey] rather than hex and cannot be
     * built with a key that is not one.
     */
    @Serializable
    private sealed class Entry {
        @Serializable
        @SerialName("secret")
        data class Secret(val privateKey: String) : Entry()

        @Serializable
        @SerialName("public")
        data object Public : Entry()
    }

    companion object {
        const val VERSION: Byte = 1
        private const val IV_LENGTH = 16

        /**
         * The discriminator is chosen here rather than inherited: the library's other
         * sealed payloads pick a variant by a version field, but these two variants have
         * different fields and the map decodes in one call this way.
         */
        private val json = Json { classDiscriminator = "type" }

        /** Reads a serialized file back. Throws `IllegalArgumentException` on an unknown version. */
        fun deserialize(serialized: ByteArray): EncryptedNostrCredentials {
            val stream = Buffer().write(serialized)
            val version = stream.readByte()
            require(version == VERSION) { "unhandled nostr credentials file version=$version" }
            require(stream.size >= IV_LENGTH) { "nostr credentials file is truncated" }
            val iv = stream.readByteArray(IV_LENGTH.toLong())
            val ciphertext = stream.readByteArray()
            return EncryptedNostrCredentials(iv, ciphertext)
        }

        /** Encrypts [credentials], keyed by x-only public key hex, under [KeyStoreNames.KEY_NO_AUTH]. */
        fun encrypt(credentials: Map<String, NostrCredential>): EncryptedNostrCredentials {
            val entries: Map<String, Entry> = credentials.mapValues { (_, credential) ->
                when (credential) {
                    is NostrCredential.Secret -> Entry.Secret(credential.privateKey.value.toHex())
                    is NostrCredential.Public -> Entry.Public
                }
            }
            val payload = json.encodeToString(entries)
            val (iv, ciphertext) = keyStoreEncryption(KeyStoreNames.KEY_NO_AUTH, payload.encodeToByteArray())
            return EncryptedNostrCredentials(iv, ciphertext)
        }
    }
}
