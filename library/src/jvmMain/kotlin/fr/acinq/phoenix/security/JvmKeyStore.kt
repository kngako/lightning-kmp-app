package fr.acinq.phoenix.security

import fr.acinq.phoenix.utils.defaultApplicationDir
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStoreException
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-protected key storage for the jvm target.
 *
 * # What this is not
 *
 * Android backs `keyStoreEncryption`/`keyStoreDecryption` with `AndroidKeyStore`, StrongBox
 * where the device has a secure element, and the key material never leaves hardware. **This
 * has no hardware backing and cannot have any.** While [unlock]ed, the derived key sits in
 * this process's heap, where anything able to read the process -- a debugger, a core dump,
 * another program running as the same user -- can recover it. A passphrase is the only
 * thing between an attacker holding the key file and the seed.
 *
 * That is the reason a desktop build carrying real funds needs an OS keychain
 * (Keychain/DPAPI/libsecret) instead, and why this one should not.
 *
 * # Construction
 *
 * ```
 * passphrase --Argon2id(salt)--> KEK --AES-256-GCM--> wraps a random DEK per key name
 *                                                     DEK --AES-256-GCM--> the seed
 * ```
 *
 * The indirection is what makes changing the passphrase cheap: it rewraps the DEKs and
 * leaves every ciphertext alone. Without it, a passphrase change would have to re-encrypt
 * and re-serialize the seed itself.
 *
 * # On the 16-byte IV
 *
 * GCM conventionally takes a 96-bit nonce, but `EncryptedSeed.V2.serialize` in commonMain
 * rejects any iv that is not 16 bytes, so this uses 128-bit ones. That is permitted, and
 * for randomly generated nonces it is the better choice anyway -- the whole risk with a
 * random GCM nonce is a repeat under one key, and 128 bits makes that collision
 * vanishingly unlikely where 96 bits merely makes it unlikely. A constraint inherited from
 * android's CBC format happens to help here.
 */
object JvmKeyStore {

    private const val STORE_FILE_NAME = "keystore.properties"
    private const val STORE_VERSION = "1"

    private const val KEY_BITS = 256
    private const val IV_BYTES = 16
    private const val SALT_BYTES = 16
    private const val GCM_TAG_BITS = 128

    // OWASP's second-preset Argon2id parameters. Roughly 100ms on a current laptop, which
    // is a cost a human pays once per launch and an attacker pays per guess.
    private const val ARGON2_MEMORY_KIB = 65536
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 1

    private val random = SecureRandom()

    private var storeFile: File? = null
    private var kek: ByteArray? = null

    val isUnlocked: Boolean
        @Synchronized get() = kek != null

    /**
     * Derives the key-encryption key from [passphrase] and holds it until [lock].
     *
     * Creates the store on first use, which means **any passphrase is accepted the first
     * time** -- there is nothing yet to check it against. It is only wrong afterwards, when
     * unwrapping a data key fails and surfaces as a [KeyStoreException].
     *
     * The caller should clear [passphrase] afterwards; this does not, since it does not own
     * the array.
     */
    @Synchronized
    fun unlock(passphrase: CharArray, storeDir: File = defaultApplicationDir()) {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        storeDir.mkdirs()
        val file = File(storeDir, STORE_FILE_NAME)
        val props = readStore(file)
        // A store carrying wrapped keys but no salt is damaged, not new. Falling through to
        // the branch below would write a fresh salt over it and turn a recoverable file --
        // restore it from a backup and the keys still unwrap -- into one whose data keys can
        // never be recovered.
        check(props.getProperty("salt") != null || props.keys.none { "$it".startsWith("key.") }) {
            "key store at ${file.absolutePath} has key material but no salt; refusing to overwrite it"
        }
        val salt = props.getProperty("salt")
            ?.let { Base64.getDecoder().decode(it) }
            ?: ByteArray(SALT_BYTES).also {
                random.nextBytes(it)
                props.setProperty("version", STORE_VERSION)
                props.setProperty("kdf", "argon2id")
                props.setProperty("argon2.memoryKiB", ARGON2_MEMORY_KIB.toString())
                props.setProperty("argon2.iterations", ARGON2_ITERATIONS.toString())
                props.setProperty("argon2.parallelism", ARGON2_PARALLELISM.toString())
                props.setProperty("salt", Base64.getEncoder().encodeToString(it))
                writeStore(file, props)
            }

        kek?.let(::zero)
        kek = deriveKek(passphrase, salt, props)
        storeFile = file
    }

    /** Zeroes the derived key. Subsequent seed operations fail until [unlock] is called again. */
    @Synchronized
    fun lock() {
        kek?.let(::zero)
        kek = null
        storeFile = null
    }

    @Synchronized
    internal fun encrypt(keyName: String, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val dek = getOrCreateDataKey(keyName)
        try {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            return iv to cipher(Cipher.ENCRYPT_MODE, dek, iv).doFinal(plaintext)
        } finally {
            zero(dek)
        }
    }

    @Synchronized
    internal fun decrypt(keyName: String, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val dek = existingDataKey(keyName)
            ?: throw KeyStoreException("no key material stored for $keyName")
        try {
            return cipher(Cipher.DECRYPT_MODE, dek, iv).doFinal(ciphertext)
        } finally {
            zero(dek)
        }
    }

    /** Unwraps the data key for [keyName], minting one if the store has none yet. */
    private fun getOrCreateDataKey(keyName: String): ByteArray =
        dataKey(keyName, createIfAbsent = true)
            ?: throw KeyStoreException("could not create key material for $keyName")

    /** Unwraps the data key for [keyName], or null if the store has never held one. */
    private fun existingDataKey(keyName: String): ByteArray? =
        dataKey(keyName, createIfAbsent = false)

    /**
     * The key names are checked against [KeyStoreNames] the way android's `KeystoreHelper`
     * checks them: an unrecognised name is a caller bug, and silently minting a key for it
     * would hide that behind a seed that cannot afterwards be decrypted.
     */
    private fun dataKey(keyName: String, createIfAbsent: Boolean): ByteArray? {
        val currentKek = kek ?: throw KeyStoreException(
            "key store is locked; call JvmKeyStore.unlock(passphrase) before reading or writing a seed"
        )
        require(keyName == KeyStoreNames.KEY_NO_AUTH || keyName == KeyStoreNames.KEY_FOR_PINCODE_V1) {
            "unhandled key=$keyName"
        }
        val file = storeFile ?: throw KeyStoreException("key store is locked")
        val props = readStore(file)

        val wrapped = props.getProperty("key.$keyName.wrapped")
        val wrapIv = props.getProperty("key.$keyName.iv")
        if (wrapped != null && wrapIv != null) {
            return try {
                cipher(
                    Cipher.DECRYPT_MODE,
                    currentKek,
                    Base64.getDecoder().decode(wrapIv),
                ).doFinal(Base64.getDecoder().decode(wrapped))
            } catch (e: javax.crypto.AEADBadTagException) {
                // The tag is what tells a wrong passphrase apart from a corrupt file, and
                // neither is recoverable here. KeyStoreException is what the graceful*
                // wrappers in commonMain map to DecryptSeedResult.Failure.KeyStoreFailure.
                throw KeyStoreException("wrong passphrase, or the key store is corrupt").initCause(e) as KeyStoreException
            }
        }
        if (!createIfAbsent) return null

        val dek = ByteArray(KEY_BITS / 8).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        props.setProperty("key.$keyName.iv", Base64.getEncoder().encodeToString(iv))
        props.setProperty(
            "key.$keyName.wrapped",
            Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE, currentKek, iv).doFinal(dek)),
        )
        writeStore(file, props)
        return dek
    }

    private fun deriveKek(passphrase: CharArray, salt: ByteArray, props: Properties): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(props.getProperty("argon2.memoryKiB")?.toInt() ?: ARGON2_MEMORY_KIB)
            .withIterations(props.getProperty("argon2.iterations")?.toInt() ?: ARGON2_ITERATIONS)
            .withParallelism(props.getProperty("argon2.parallelism")?.toInt() ?: ARGON2_PARALLELISM)
            .build()
        // Encoded here rather than via String, so the passphrase never lands in an immutable
        // object the caller cannot clear.
        val bytes = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(passphrase)).let { buf ->
            ByteArray(buf.remaining()).also { buf.get(it) }
        }
        try {
            return ByteArray(KEY_BITS / 8).also {
                Argon2BytesGenerator().apply { init(params) }.generateBytes(bytes, it)
            }
        } finally {
            zero(bytes)
        }
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun readStore(file: File): Properties = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    /**
     * Written to a sibling and moved into place: a half-written store is one whose wrapped
     * data key no longer decrypts, which loses the seed rather than merely failing.
     */
    private fun writeStore(file: File, props: Properties) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { props.store(it, "phoenix jvm key store -- see JvmKeyStore.kt") }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Best effort only. The jvm is free to have copied any of this during a gc, and nothing
     * here can reach those copies.
     */
    private fun zero(bytes: ByteArray) = Arrays.fill(bytes, 0)
}
