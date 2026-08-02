/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.utils.converters.AmountConversionResult
import fr.acinq.phoenix.utils.converters.AmountConverter
import fr.acinq.phoenix.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.utils.converters.AmountConverter.toMilliSatoshi
import fr.acinq.phoenix.utils.converters.AmountConverter.toUnit
import fr.acinq.phoenix.utils.converters.ComplexAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [AmountConverter.convertToComplexAmount], which turns a user-typed amount into a
 * [ComplexAmount]. This is the counterpart of [Parser.parseBip21Uri] for manually entered amounts.
 */
class AmountConverterTest {

    private fun rate(price: Double, fiat: FiatCurrency = FiatCurrency.USD) = ExchangeRate.BitcoinPriceRate(
        fiatCurrency = fiat,
        price = price,
        source = "test",
        timestampMillis = 0L
    )

    // -- blank / invalid input

    @Test
    fun blank_input_returns_null() {
        assertNull(AmountConverter.convertToComplexAmount(null, BitcoinUnit.Sat, null))
        assertNull(AmountConverter.convertToComplexAmount("", BitcoinUnit.Sat, null))
        assertNull(AmountConverter.convertToComplexAmount("   ", BitcoinUnit.Sat, null))
    }

    @Test
    fun unparseable_input_is_rejected() {
        listOf("abc", "1.2.3", "1 000", "--1", "1e", "٣").forEach { input ->
            assertEquals(
                AmountConversionResult.Error.InvalidInput,
                AmountConverter.convertToComplexAmount(input, BitcoinUnit.Sat, null),
                "expected `$input` to be rejected"
            )
        }
    }

    // -- bitcoin units

    @Test
    fun converts_bitcoin_units() {
        assertEquals(100_000_000_000.msat, complexAmount("1", BitcoinUnit.Btc).amount)
        assertEquals(50_000_000_000.msat, complexAmount("0.5", BitcoinUnit.Btc).amount)
        assertEquals(100_000_000.msat, complexAmount("1", BitcoinUnit.MBtc).amount)
        assertEquals(100_000.msat, complexAmount("1", BitcoinUnit.Bit).amount)
        assertEquals(1_000.msat, complexAmount("1", BitcoinUnit.Sat).amount)
        assertEquals(1.msat, complexAmount("0.001", BitcoinUnit.Sat).amount)
    }

    /** Unlike bip-21 parsing, manual input accepts a comma as the decimal separator. */
    @Test
    fun accepts_comma_as_decimal_separator() {
        assertEquals(
            complexAmount("0.5", BitcoinUnit.Btc).amount,
            complexAmount("0,5", BitcoinUnit.Btc).amount
        )
    }

    @Test
    fun rejects_negative_amounts() {
        assertEquals(
            AmountConversionResult.Error.AmountNegative,
            AmountConverter.convertToComplexAmount("-1", BitcoinUnit.Sat, null)
        )
        assertEquals(
            AmountConversionResult.Error.AmountNegative,
            AmountConverter.convertToComplexAmount("-0.5", BitcoinUnit.Btc, null)
        )
    }

    @Test
    fun rejects_amounts_above_max_supply() {
        assertEquals(
            AmountConversionResult.Error.AmountTooLarge,
            AmountConverter.convertToComplexAmount("22000000", BitcoinUnit.Btc, null)
        )
        // 21e6 btc itself is accepted -- the check is strictly greater-than
        assertEquals(21_000_000_00000_000_000.msat, complexAmount("21000000", BitcoinUnit.Btc).amount)
    }

    @Test
    fun bitcoin_amount_without_rate_has_no_fiat() {
        val res = complexAmount("1000", BitcoinUnit.Sat, rate = null)
        assertEquals(1_000_000.msat, res.amount)
        assertNull(res.fiat, "a missing rate must not prevent the amount from being converted")
    }

    @Test
    fun bitcoin_amount_with_rate_computes_fiat() {
        val res = complexAmount("0.5", BitcoinUnit.Btc, rate = rate(50_000.0))
        assertEquals(50_000_000_000.msat, res.amount)
        assertEquals(25_000.0, res.fiat?.value)
        assertEquals(FiatCurrency.USD, res.fiat?.currency)
    }

    // -- fiat

    @Test
    fun fiat_without_rate_is_rejected() {
        assertEquals(
            AmountConversionResult.Error.RateUnavailable,
            AmountConverter.convertToComplexAmount("100", FiatCurrency.USD, null)
        )
    }

    @Test
    fun converts_fiat_to_bitcoin() {
        val res = complexAmount("100", FiatCurrency.USD, rate = rate(50_000.0))
        // 100 usd at 50k usd/btc = 0.002 btc = 200_000 sat
        assertEquals(200_000_000.msat, res.amount)
    }

    /**
     * When converting *from* fiat the sub-satoshi part is dropped, so that we never produce an
     * amount that services which don't understand millisatoshis would choke on.
     */
    @Test
    fun fiat_conversion_truncates_to_whole_satoshis() {
        listOf(30_000.0, 37_777.0, 61_111.11, 12_345.67).forEach { price ->
            listOf("1", "7", "13.37").forEach { input ->
                val res = complexAmount(input, FiatCurrency.USD, rate = rate(price))
                assertEquals(
                    0L, res.amount.msat % 1_000L,
                    "converting $input usd at $price should yield a whole number of sats, got ${res.amount}"
                )
            }
        }
    }

    /** The fiat side keeps the value the user actually typed, not a lossy round-trip of it. */
    @Test
    fun fiat_amount_preserves_user_input() {
        val res = complexAmount("13.37", FiatCurrency.USD, rate = rate(30_000.0))
        assertEquals(13.37, res.fiat?.value)
        assertEquals(FiatCurrency.USD, res.fiat?.currency)
    }

    @Test
    fun rejects_fiat_amount_above_max_supply() {
        // 1 btc = 1 usd, so 22e6 usd is more than the total supply
        assertEquals(
            AmountConversionResult.Error.AmountTooLarge,
            AmountConverter.convertToComplexAmount("22000000", FiatCurrency.USD, rate(1.0))
        )
    }

    @Test
    fun rejects_negative_fiat_amount() {
        assertEquals(
            AmountConversionResult.Error.AmountNegative,
            AmountConverter.convertToComplexAmount("-100", FiatCurrency.USD, rate(50_000.0))
        )
    }

    // -- unit conversion round-trips

    @Test
    fun to_unit_is_the_inverse_of_to_millisatoshi() {
        val amounts = listOf(1L, 999L, 1_000L, 1_234_567L, 100_000_000_000L)
        BitcoinUnit.entries.forEach { unit ->
            amounts.forEach { msat ->
                assertEquals(
                    msat.msat, msat.msat.toUnit(unit).toMilliSatoshi(unit),
                    "round-trip through $unit lost precision for $msat msat"
                )
            }
        }
    }

    @Test
    fun to_fiat_uses_the_btc_price() {
        assertEquals(50_000.0, 100_000_000_000.msat.toFiat(50_000.0))
        assertEquals(25_000.0, 50_000_000_000.msat.toFiat(50_000.0))
        assertTrue(1_000.msat.toFiat(50_000.0) > 0.0)
    }

    private fun complexAmount(
        input: String,
        unit: fr.acinq.phoenix.data.CurrencyUnit,
        rate: ExchangeRate.BitcoinPriceRate? = null
    ): ComplexAmount {
        val res = AmountConverter.convertToComplexAmount(input, unit, rate)
        assertIs<ComplexAmount>(res, "expected `$input` in $unit to convert successfully, got $res")
        return res
    }
}
