/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.PreferredFiatCurrencies
import fr.acinq.phoenix.data.UserTheme
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.utils.preferences.InternalPrefs
import fr.acinq.phoenix.utils.preferences.PreferredBitcoinUnits
import fr.acinq.phoenix.utils.preferences.UserPrefs


typealias CF = ControllerFactory

val LocalTheme = staticCompositionLocalOf { UserTheme.SYSTEM }
val LocalWalletId = compositionLocalOf<WalletId?> { null }
val LocalBusiness = compositionLocalOf<PhoenixBusiness?> { null }
val LocalUserPrefs = staticCompositionLocalOf<UserPrefs?> { null }
val LocalInternalPrefs = staticCompositionLocalOf<InternalPrefs?> { null }
val LocalControllerFactory = staticCompositionLocalOf<ControllerFactory?> { null }
val LocalNavController = staticCompositionLocalOf<NavController?> { null }
val LocalBitcoinUnits = compositionLocalOf { PreferredBitcoinUnits(primary = BitcoinUnit.Sat) }
val LocalFiatCurrencies = compositionLocalOf { PreferredFiatCurrencies(primary = FiatCurrency.USD, others = emptyList()) }
val LocalExchangeRatesMap = compositionLocalOf<Map<FiatCurrency, ExchangeRate.BitcoinPriceRate>> { emptyMap() }
val LocalShowInFiat = compositionLocalOf { false }
val isDarkTheme: Boolean
    @Composable
    get() = LocalTheme.current.let { it == UserTheme.DARK || (it == UserTheme.SYSTEM && isSystemInDarkTheme()) }

val preferredAmountUnit: CurrencyUnit
    @Composable
    get() = if (LocalShowInFiat.current) LocalFiatCurrencies.current.primary else LocalBitcoinUnits.current.primary

val primaryFiatRate: ExchangeRate.BitcoinPriceRate?
    @Composable
    get() = LocalFiatCurrencies.current.primary.let { prefFiat -> LocalExchangeRatesMap.current[prefFiat] }
