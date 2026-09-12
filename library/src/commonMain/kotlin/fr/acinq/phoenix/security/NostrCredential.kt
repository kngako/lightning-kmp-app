package fr.acinq.phoenix.security

import fr.acinq.bitcoin.PrivateKey

/**
 * What this device holds for a nostr public key: the secret that signs as it, or
 * only the key itself.
 *
 * The entry type of `nostr-credentials.dat` ([EncryptedNostrCredentials]). A
 * [Public] entry is a profile the user has signed in to look at without pasting a
 * secret; it can become a [Secret] later, in one write, because both live in one
 * file under one map key. See docs/npub-sign-in.md in the consuming app.
 */
sealed class NostrCredential {

    /** The private key. Its public key is the map key the entry is filed under. */
    data class Secret(val privateKey: PrivateKey) : NostrCredential() {
        /** The key must never reach a log. `data class` would print it. */
        override fun toString(): String = "NostrCredential.Secret(<redacted>)"
    }

    /** No secret: the device knows the public key and nothing else. */
    data object Public : NostrCredential()
}
