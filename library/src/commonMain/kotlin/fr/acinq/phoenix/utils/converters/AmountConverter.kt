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

package fr.acinq.phoenix.utils.converters

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.LocalExchangeRatesMap
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency


sealed class AmountConversionResult {
    sealed class Error : AmountConversionResult() {
        data object InvalidInput : Error()
        data object AmountTooLarge: Error()
        data object AmountNegative: Error()
        data object RateUnavailable: Error()
    }
}

// wrapper for ComplexAmount
data class FiatAmount(val value: Double, val currency: FiatCurrency, val exchangeRate: ExchangeRate)

data class ComplexAmount(val amount: MilliSatoshi, val fiat: FiatAmount?): AmountConversionResult()

object AmountConverter {
    private val log = Logger.withTag("AmountConverter")

    /**
     * This methods converts a string [input] expressed in [unit] (can be fiat or bitcoin) into a standardised
     * [ComplexAmount] object, using the provided fiat rate for conversion.
     *
     * Returns an [AmountConversionResult.Error] if conversion cannot be done.
     */
    fun convertToComplexAmount(
        input: String?,
        unit: CurrencyUnit,
        rate: ExchangeRate.BitcoinPriceRate?,
    ): AmountConversionResult? {
        log.d("amount input update [ amount=$input unit=$unit with rate=$rate ]")

        if (input.isNullOrBlank()) {
            return null
        }

        val amount = try {
            // note: DecimalFormat.parse works somewhat, but can be confused when the input contains a mix of "." and "," and returns 0
            // instead we used String.toDouble, which expects "." as decimal separator
            input.replace(",", ".").toDouble()
        } catch (e: Exception) {
            log.d("could not parse input=$input: ", e)
            return AmountConversionResult.Error.InvalidInput
        }

        return when (unit) {
            is FiatCurrency -> {
                if (rate == null) {
                    log.w("cannot convert fiat amount to bitcoin with a null rate")
                    AmountConversionResult.Error.RateUnavailable
                } else {
                    // convert fiat amount to millisat, but truncate the msat part to avoid issues with
                    // services/wallets that don't understand millisats. We only do this when converting
                    // from fiat. If amount is in btc, we use the real value entered by the user.
                    val msat = amount.toMilliSatoshi(rate.price).truncateToSatoshi().toMilliSatoshi()
                    if (msat.toUnit(BitcoinUnit.Btc) > 21e6) {
                        AmountConversionResult.Error.AmountTooLarge
                    } else if (msat < 0.msat) {
                        AmountConversionResult.Error.AmountNegative
                    } else {
                        ComplexAmount(msat, FiatAmount(amount, unit, rate))
                    }
                }
            }
            is BitcoinUnit -> {
                val msat = amount.toMilliSatoshi(unit)
                if (msat.toUnit(BitcoinUnit.Btc) > 21e6) {
                    AmountConversionResult.Error.AmountTooLarge
                } else if (msat < 0.msat) {
                    AmountConversionResult.Error.AmountNegative
                } else if (rate == null) {
                    // conversion is not possible but that should not stop a payment from happening
                    ComplexAmount(amount = msat, fiat = null)
                } else {
                    val fiat = msat.toFiat(rate.price)
                    ComplexAmount(amount = msat, fiat = FiatAmount(fiat, rate.fiatCurrency, rate))
                }
            }
            else -> {
                null
            }
        }
    }

    /** Converts this [Double] amount to [MilliSatoshi], assuming that this amount is denominated in fiat. */
    fun Double.toMilliSatoshi(fiatRate: Double): MilliSatoshi = (this / fiatRate).toMilliSatoshi(BitcoinUnit.Btc)
    @Composable
    fun Double.toMilliSatoshi(fiat: FiatCurrency): MilliSatoshi? = LocalExchangeRatesMap.current[fiat]?.let { this.toMilliSatoshi(it.price) }

    /** Converts this [Double] amount to [MilliSatoshi], assuming that this amount is denominated in the given Bitcoin [unit]. */
    fun Double.toMilliSatoshi(unit: BitcoinUnit): MilliSatoshi = when (unit) {
        BitcoinUnit.Sat -> this.toBigDecimal().moveDecimalPoint(3).longValue(false).msat
        BitcoinUnit.Bit -> this.toBigDecimal().moveDecimalPoint(5).longValue(false).msat
        BitcoinUnit.MBtc -> this.toBigDecimal().moveDecimalPoint(8).longValue(false).msat
        BitcoinUnit.Btc -> this.toBigDecimal().moveDecimalPoint(11).longValue(false).msat
    }

    /**
     * Converts [MilliSatoshi] to another Bitcoin unit.
     *
     * Note: [doubleValue] must be called with `exactRequired = false`. Most amounts have no exact binary
     * representation once shifted (1 sat = 0.00000001 btc, for example), and the default `true` makes the
     * conversion throw an [ArithmeticException] instead of rounding to the nearest double.
     */
    fun MilliSatoshi.toUnit(unit: BitcoinUnit): Double = when (unit) {
        BitcoinUnit.Sat -> this.msat.toBigDecimal().moveDecimalPoint(-3).doubleValue(false)
        BitcoinUnit.Bit -> this.msat.toBigDecimal().moveDecimalPoint(-5).doubleValue(false)
        BitcoinUnit.MBtc -> this.msat.toBigDecimal().moveDecimalPoint(-8).doubleValue(false)
        BitcoinUnit.Btc -> this.msat.toBigDecimal().moveDecimalPoint(-11).doubleValue(false)
    }

    /** Converts [MilliSatoshi] to a fiat amount. */
    fun MilliSatoshi.toFiat(rate: Double): Double = this.toUnit(BitcoinUnit.Btc) * rate
}