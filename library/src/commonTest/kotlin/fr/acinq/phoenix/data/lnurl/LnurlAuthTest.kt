/*
 * Copyright 2022 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.data.lnurl

import fr.acinq.bitcoin.*
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.secp256k1.Hex
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LnurlAuthTest {
    private val mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val seed = MnemonicCode.toSeed(mnemonics, passphrase = "").toByteVector()
    private val keyManager = LocalKeyManager(seed, Chain.Testnet3, remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41")

    @Test
    fun specs_test_vectors() {
        // Test vector from spec:
        // https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
        val domain = "site.com"
        val hashingKey = "0x7d417a6a5e9a6a4a879aeaba11a11838764c8fa2b959c242d43dea682b3e409b01"
        val expectedPath = "m/138'/3751473387/2829804099/4228872783/4134047485"
        assertEquals(KeyPath(expectedPath), LnurlAuth.getDerivationPathForDomain(domain, Hex.decode(hashingKey)))
    }

    @Test
    fun test_default_scheme() {
        val auth = LnurlAuth(
            initialUrl = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=e94e9e54d97164751db976c347a1d325167d48c0f6c2e08688bec185fa5fc20a"),
            k1 = "e94e9e54d97164751db976c347a1d325167d48c0f6c2e08688bec185fa5fc20a"
        )
        val linkingKey = LnurlAuth.getAuthLinkingKey(keyManager, auth.initialUrl, LnurlAuth.Scheme.DEFAULT_SCHEME)
        assertEquals("03702494face111dcd61be4ab4a13fa4cd4ac720b2d3b47e95feee58484f573630", linkingKey.publicKey().toString())

        val signedChallenge = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(auth.k1), privateKey = linkingKey)).toHex()
        val expectedSignature = "3044022078c05792b76a8772c790d4d9d73c793f6cd34ea5e1a1a70bb5cd600cbc3452b902204e1a63654dcc8fcbf07f3b56b4dd4a37ddbdd6bd90738091c891f65ac53bc7b0"
        assertEquals(expectedSignature, signedChallenge)
    }

    @Test
    fun test_android_legacy_scheme() {
        val auth = LnurlAuth(
            initialUrl = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425&hmac=75344d9151fe788345e620aa3de0e69b51698e759fd667272e3ea682a2bbcd12"),
            k1 = "179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425"
        )
        val linkingKey = LnurlAuth.getAuthLinkingKey(keyManager, auth.initialUrl, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        assertEquals("024d82b199464c9568f5cce92cf7370a8154d9ec0571905b596a4e9dcae69136d8", linkingKey.publicKey().toString())

        val signedChallenge = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(auth.k1), privateKey = linkingKey)).toHex()
        val expectedSignature = "3044022056299db29f515fa2941e5212bfc6de7bd64ca0edd6067e3575a4753ca00be1ec02200f63e9905eef29b1ba8b54c79cfcf41c1380bc6eeb8ca8e5c2748a79c962b663"
        assertEquals(expectedSignature, signedChallenge)
    }

    @Test
    fun test_legacy_domains() {
        listOf(
            Url("https://auth.geyser.fund") to "geyser.fund",
            Url("https://api.kollider.xyz") to "kollider.xyz",
            Url("https://api.lnmarkets.com") to "lnmarkets.com",
            Url("https://getalby.com") to "getalby.com",
            Url("https://lightning.video") to "lightning.video",
            Url("https://api.loft.trade") to "loft.trade",
            Url("https://lnshort.it") to "lnshort.it",
            Url("https://stacker.news") to "stacker.news",
        ).forEach { (url, expectedDomain) ->
            assertEquals(expectedDomain, LnurlAuth.LegacyDomain.filterDomain(url))
        }
    }

    @Test
    fun test_non_legacy_domains() {
        listOf(
            Url("https://auth.google.com"),
            Url("https://staging.foo.bar"),
            Url("https://apı̇.lnmarkets.com"), // u+0307  ̇ + u+0131 ı = ı̇
            Url("https://api2.lnmarkets.com"),
            Url("https://x.lnmarkets.com"),
            Url("https://login.stacker.news"),
            Url("http://one.herokuapp.com"),
            Url("http://two.herokuapp.com"),
        ).forEach { url ->
            assertEquals(url.host, LnurlAuth.LegacyDomain.filterDomain(url))
        }
    }

    /** Checks that the default scheme generates a DIFFERENT key/sig tuple for a non-legacy domain than the legacy scheme. */
    @Test
    fun legacy_domain_different_keys() {
        val seed = MnemonicCode.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", "")
        val kmpKeyManager = LocalKeyManager(
            seed = seed.byteVector(),
            chain = Chain.Testnet3,
            remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        )

        val k1 = "179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425"
        val url = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=$k1&hmac=75344d9151fe788345e620aa3de0e69b51698e759fd667272e3ea682a2bbcd12")

        // key when using the legacy friendly scheme
        val legacyAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        val legacySignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = legacyAuthKey)).toHex()
        // key when using the default scheme
        val defaultAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.DEFAULT_SCHEME)
        val defaultSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = defaultAuthKey)).toHex()

        val expectedLegacyPubkey = "024d82b199464c9568f5cce92cf7370a8154d9ec0571905b596a4e9dcae69136d8"
        val expectedNewPubkey = "03702494face111dcd61be4ab4a13fa4cd4ac720b2d3b47e95feee58484f573630"
        assertEquals(expectedLegacyPubkey, legacyAuthKey.publicKey().toString())
        assertEquals(expectedLegacyPubkey, legacyAuthKey.publicKey().toString())
        assertEquals(expectedNewPubkey, defaultAuthKey.publicKey().toString())

        val expectedLegacySig = "3044022056299db29f515fa2941e5212bfc6de7bd64ca0edd6067e3575a4753ca00be1ec02200f63e9905eef29b1ba8b54c79cfcf41c1380bc6eeb8ca8e5c2748a79c962b663"
        val expectedNewSig = "304402204cc2411cebd5c5c9722da29aad92788434026744fd6e5aa6a98a5a3f30f2595f022044fdeeb8158611a270ee933510779b117493653011dc3e30172167ca721056a5"
        assertEquals(expectedLegacySig, legacySignedK1)
        assertEquals(expectedLegacySig, legacySignedK1)
        assertEquals(expectedNewSig, defaultSignedK1)
    }

    /** Checks that the app generates the SAME pubkey/signature for a non-legacy domain whatever the scheme used. */
    @Test
    fun standard_domain_same_key() {
        val seed = MnemonicCode.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", "")
        val kmpKeyManager = LocalKeyManager(
            seed = seed.byteVector(),
            chain = Chain.Testnet3,
            remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        )

        val k1 = "32c56da24a28e09d24832e1cba0cc391049c48036c197e228c7656d022a5eb1f"
        val url = Url("https://foo.bar.com/auth?tag=login&k1=$k1")

        // key when using the legacy friendly scheme
        val legacyAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        val legacySignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = legacyAuthKey)).toHex()
        // key when using the default scheme
        val defaultNewAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.DEFAULT_SCHEME)
        val defaultNewSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = defaultNewAuthKey)).toHex()

        val expectedPubkey = "02ebfff275eccdd929a3843eff9481a53b0445cdae9a931fd43baa91dcd35836e9"
        assertEquals(expectedPubkey, legacyAuthKey.publicKey().toString())
        assertEquals(expectedPubkey, defaultNewAuthKey.publicKey().toString())

        val expectedSignature = "3045022100f3710b22bd3433b1aa56fa61b1ad9ced79d4e5010430d0afa8d6c7dc77481ef502201a80ab49a2facf8810652bf9e0f85d6c834f6e9fd59f312e7eeece36e51ce957"
        assertEquals(expectedSignature, legacySignedK1)
        assertEquals(expectedSignature, defaultNewSignedK1)
    }
}
