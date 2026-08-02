package fr.acinq.phoenix.data

import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.library.generated.resources.Res
import fr.acinq.phoenix.library.generated.resources.currency_ars_bm
import fr.acinq.phoenix.library.generated.resources.currency_ars_official
import fr.acinq.phoenix.library.generated.resources.currency_cup_fm
import fr.acinq.phoenix.library.generated.resources.currency_cup_official
import fr.acinq.phoenix.library.generated.resources.currency_lbp_bm
import fr.acinq.phoenix.library.generated.resources.currency_lbp_official
import fr.acinq.phoenix.utils.extensions.isOnion
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString


sealed interface CurrencyUnit {
    /** Code that should be displayed in the UI. */
    val displayCode: String
}

@Serializable
enum class BitcoinUnit(override val displayCode: String) : CurrencyUnit {
    Sat("sat"), Bit("bit"), MBtc("mbtc"), Btc("btc");

    override fun toString(): String {
        return super.toString().lowercase()
    }

    companion object {
        val values = entries

        fun valueOfOrNull(code: String): BitcoinUnit? = try {
            valueOf(code)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * @param flag when multiple countries use that currency, use the flag of the country with highest GDP
 */
@Serializable
enum class FiatCurrency(override val displayCode: String, val flag: String = "🏳️") : CurrencyUnit {
    AED(displayCode = "AED", flag = "🇦🇪"), // United Arab Emirates Dirham
    AFN(displayCode = "AFN", flag = "🇦🇫"), // Afghan Afghani
    ALL(displayCode = "ALL", flag = "🇦🇱"), // Albanian Lek
    AMD(displayCode = "AMD", flag = "🇦🇲"), // Armenian Dram
    ANG(displayCode = "ANG", flag = "🇳🇱"), // Netherlands Antillean Guilder
    AOA(displayCode = "AOA", flag = "🇦🇴"), // Angolan Kwanza
    ARS_BM(displayCode = "ARS", flag = "🇦🇷"), // Argentine Peso (blue market)
    ARS(displayCode = "ARS_OFF", flag = "🇦🇷"), // Argentine Peso (official rate)
    AUD(displayCode = "AUD", flag = "🇦🇺"), // Australian Dollar
    AWG(displayCode = "AWG", flag = "🇦🇼"), // Aruban Florin
    AZN(displayCode = "AZN", flag = "🇦🇿"), // Azerbaijani Manat
    BAM(displayCode = "BAM", flag = "🇧🇦"), // Bosnia-Herzegovina Convertible Mark
    BBD(displayCode = "BBD", flag = "🇧🇧"), // Barbadian Dollar
    BDT(displayCode = "BDT", flag = "🇧🇩"), // Bangladeshi Taka
    BGN(displayCode = "BGN", flag = "🇧🇬"), // Bulgarian Lev
    BHD(displayCode = "BHD", flag = "🇧🇭"), // Bahraini Dinar
    BIF(displayCode = "BIF", flag = "🇧🇮"), // Burundian Franc
    BMD(displayCode = "BMD", flag = "🇧🇲"), // Bermudan Dollar
    BND(displayCode = "BND", flag = "🇧🇳"), // Brunei Dollar
    BOB(displayCode = "BOB", flag = "🇧🇴"), // Bolivian Boliviano
    BRL(displayCode = "BRL", flag = "🇧🇷"), // Brazilian Real
    BSD(displayCode = "BSD", flag = "🇧🇸"), // Bahamian Dollar
    BTN(displayCode = "BTN", flag = "🇧🇹"), // Bhutanese Ngultrum
    BWP(displayCode = "BWP", flag = "🇧🇼"), // Botswanan Pula
    BZD(displayCode = "BZD", flag = "🇧🇿"), // Belize Dollar
    CAD(displayCode = "CAD", flag = "🇨🇦"), // Canadian Dollar
    CDF(displayCode = "CDF", flag = "🇨🇩"), // Congolese Franc
    CHF(displayCode = "CHF", flag = "🇨🇭"), // Swiss Franc
    CLP(displayCode = "CLP", flag = "🇨🇱"), // Chilean Peso
    CNH(displayCode = "CNH", flag = "🇨🇳"), // Chinese Yuan (offshore)
    CNY(displayCode = "CNY", flag = "🇨🇳"), // Chinese Yuan (onshore)
    COP(displayCode = "COP", flag = "🇨🇴"), // Colombian Peso
    CRC(displayCode = "CRC", flag = "🇨🇷"), // Costa Rican Colón
    CUP_FM(displayCode = "CUP", flag = "🇨🇺"), // Cuban Peso (free market)
    CUP(displayCode = "CUP_OFF", flag = "🇨🇺"), // Cuban Peso (official rate)
    CVE(displayCode = "CVE", flag = "🇨🇻"), // Cape Verdean Escudo
    CZK(displayCode = "CZK", flag = "🇨🇿"), // Czech Republic Koruna
    DJF(displayCode = "DJF", flag = "🇩🇯"), // Djiboutian Franc
    DKK(displayCode = "DKK", flag = "🇩🇰"), // Danish Krone
    DOP(displayCode = "DOP", flag = "🇩🇴"), // Dominican Peso
    DZD(displayCode = "DZD", flag = "🇩🇿"), // Algerian Dinar
    EGP(displayCode = "EGP", flag = "🇪🇬"), // Egyptian Pound
    ERN(displayCode = "ERN", flag = "🇪🇷"), // Eritrean Nakfa
    ETB(displayCode = "ETB", flag = "🇪🇹"), // Ethiopian Birr
    EUR(displayCode = "EUR", flag = "🇪🇺"), // Euro
    FJD(displayCode = "FJD", flag = "🇫🇯"), // Fijian Dollar
    FKP(displayCode = "FKP", flag = "🇫🇰"), // Falkland Islands Pound
    GBP(displayCode = "GBP", flag = "🇬🇧"), // British Pound Sterling
    GEL(displayCode = "GEL", flag = "🇬🇪"), // Georgian Lari
    GHS(displayCode = "GHS", flag = "🇬🇭"), // Ghanaian Cedi
    GIP(displayCode = "GIP", flag = "🇬🇮"), // Gibraltar Pound
    GMD(displayCode = "GMD", flag = "🇬🇲"), // Gambian Dalasi
    GNF(displayCode = "GNF", flag = "🇬🇳"), // Guinean Franc
    GTQ(displayCode = "GTQ", flag = "🇬🇹"), // Guatemalan Quetzal
    GYD(displayCode = "GYD", flag = "🇬🇾"), // Guyanaese Dollar
    HKD(displayCode = "HKD", flag = "🇭🇰"), // Hong Kong Dollar
    HNL(displayCode = "HNL", flag = "🇭🇳"), // Honduran Lempira
    HRK(displayCode = "HRK", flag = "🇭🇷"), // Croatian Kuna
    HTG(displayCode = "HTG", flag = "🇭🇹"), // Haitian Gourde
    HUF(displayCode = "HUF", flag = "🇭🇺"), // Hungarian Forint
    IDR(displayCode = "IDR", flag = "🇮🇩"), // Indonesian Rupiah
    ILS(displayCode = "ILS", flag = "🇮🇱"), // Israeli New Sheqel
    INR(displayCode = "INR", flag = "🇮🇳"), // Indian Rupee
    IQD(displayCode = "IQD", flag = "🇮🇶"), // Iraqi Dinar
    IRR(displayCode = "IRR", flag = "🇮🇷"), // Iranian Rial
    ISK(displayCode = "ISK", flag = "🇮🇸"), // Icelandic Króna
    JEP(displayCode = "JEP", flag = "🇯🇪"), // Jersey Pound
    JMD(displayCode = "JMD", flag = "🇯🇲"), // Jamaican Dollar
    JOD(displayCode = "JOD", flag = "🇯🇴"), // Jordanian Dinar
    JPY(displayCode = "JPY", flag = "🇯🇵"), // Japanese Yen
    KES(displayCode = "KES", flag = "🇰🇪"), // Kenyan Shilling
    KGS(displayCode = "KGS", flag = "🇰🇬"), // Kyrgystani Som
    KHR(displayCode = "KHR", flag = "🇰🇭"), // Cambodian Riel
    KMF(displayCode = "KMF", flag = "🇰🇲"), // Comorian Franc
    KPW(displayCode = "KPW", flag = "🇰🇵"), // North Korean Won
    KRW(displayCode = "KRW", flag = "🇰🇷"), // South Korean Won
    KWD(displayCode = "KWD", flag = "🇰🇼"), // Kuwaiti Dinar
    KYD(displayCode = "KYD", flag = "🇰🇾"), // Cayman Islands Dollar
    KZT(displayCode = "KZT", flag = "🇰🇿"), // Kazakhstani Tenge
    LAK(displayCode = "LAK", flag = "🇱🇦"), // Laotian Kip
    LBP_BM(displayCode = "LBP", flag = "🇱🇧"), // Lebanese Pound (black market)
    LBP(displayCode = "LBP_OFF", flag = "🇱🇧"), // Lebanese Pound (official rate)
    LKR(displayCode = "LKR", flag = "🇱🇰"), // Sri Lankan Rupee
    LRD(displayCode = "LRD", flag = "🇱🇷"), // Liberian Dollar
    LSL(displayCode = "LSL", flag = "🇱🇸"), // Lesotho Loti
    LYD(displayCode = "LYD", flag = "🇱🇾"), // Libyan Dinar
    MAD(displayCode = "MAD", flag = "🇲🇦"), // Moroccan Dirham
    MDL(displayCode = "MDL", flag = "🇲🇩"), // Moldovan Leu
    MGA(displayCode = "MGA", flag = "🇲🇬"), // Malagasy Ariary
    MKD(displayCode = "MKD", flag = "🇲🇰"), // Macedonian Denar
    MMK(displayCode = "MMK", flag = "🇲🇲"), // Myanma Kyat
    MNT(displayCode = "MNT", flag = "🇲🇳"), // Mongolian Tugrik
    MOP(displayCode = "MOP", flag = "🇲🇴"), // Macanese Pataca
    MUR(displayCode = "MUR", flag = "🇲🇺"), // Mauritian Rupee
    MVR(displayCode = "MVR", flag = "🇲🇻"), // Maldivian Rufiyaa
    MWK(displayCode = "MWK", flag = "🇲🇼"), // Malawian Kwacha
    MXN(displayCode = "MXN", flag = "🇲🇽"), // Mexican Peso
    MYR(displayCode = "MYR", flag = "🇲🇾"), // Malaysian Ringgit
    MZN(displayCode = "MZN", flag = "🇲🇿"), // Mozambican Metical
    NAD(displayCode = "NAD", flag = "🇳🇦"), // Namibian Dollar
    NGN(displayCode = "NGN", flag = "🇳🇬"), // Nigerian Naira
    NIO(displayCode = "NIO", flag = "🇳🇮"), // Nicaraguan Córdoba
    NOK(displayCode = "NOK", flag = "🇳🇴"), // Norwegian Krone
    NPR(displayCode = "NPR", flag = "🇳🇵"), // Nepalese Rupee
    NZD(displayCode = "NZD", flag = "🇳🇿"), // New Zealand Dollar
    OMR(displayCode = "OMR", flag = "🇴🇲"), // Omani Rial
    PAB(displayCode = "PAB", flag = "🇵🇦"), // Panamanian Balboa
    PEN(displayCode = "PEN", flag = "🇵🇪"), // Peruvian Sol
    PGK(displayCode = "PGK", flag = "🇵🇬"), // Papua New Guinean Kina
    PHP(displayCode = "PHP", flag = "🇵🇭"), // Philippine Peso
    PKR(displayCode = "PKR", flag = "🇵🇰"), // Pakistani Rupee
    PLN(displayCode = "PLN", flag = "🇵🇱"), // Polish Zloty
    PYG(displayCode = "PYG", flag = "🇵🇾"), // Paraguayan Guarani
    QAR(displayCode = "QAR", flag = "🇶🇦"), // Qatari Rial
    RON(displayCode = "RON", flag = "🇷🇴"), // Romanian Leu
    RSD(displayCode = "RSD", flag = "🇷🇸"), // Serbian Dinar
    RUB(displayCode = "RUB", flag = "🇷🇺"), // Russian Ruble
    RWF(displayCode = "RWF", flag = "🇷🇼"), // Rwandan Franc
    SAR(displayCode = "SAR", flag = "🇸🇦"), // Saudi Riyal
    SBD(displayCode = "SBD", flag = "🇸🇧"), // Solomon Islands Dollar
    SCR(displayCode = "SCR", flag = "🇸🇨"), // Seychellois Rupee
    SDG(displayCode = "SDG", flag = "🇸🇩"), // Sudanese Pound
    SEK(displayCode = "SEK", flag = "🇸🇪"), // Swedish Krona
    SGD(displayCode = "SGD", flag = "🇸🇬"), // Singapore Dollar
    SHP(displayCode = "SHP", flag = "🇸🇭"), // Saint Helena Pound
    SLL(displayCode = "SLL", flag = "🇸🇱"), // Sierra Leonean Leone
    SOS(displayCode = "SOS", flag = "🇸🇴"), // Somali Shilling
    SRD(displayCode = "SRD", flag = "🇸🇷"), // Surinamese Dollar
    SYP(displayCode = "SYP", flag = "🇸🇾"), // Syrian Pound
    SZL(displayCode = "SZL", flag = "🇸🇿"), // Swazi Lilangeni
    THB(displayCode = "THB", flag = "🇹🇭"), // Thai Baht
    TJS(displayCode = "TJS", flag = "🇹🇯"), // Tajikistani Somoni
    TMT(displayCode = "TMT", flag = "🇹🇲"), // Turkmenistani Manat
    TND(displayCode = "TND", flag = "🇹🇳"), // Tunisian Dinar
    TOP(displayCode = "TOP", flag = "🇹🇴"), // Tongan Paʻanga
    TRY(displayCode = "TRY", flag = "🇹🇷"), // Turkish Lira
    TTD(displayCode = "TTD", flag = "🇹🇹"), // Trinidad and Tobago Dollar
    TWD(displayCode = "TWD", flag = "🇹🇼"), // Taiwan Dollar
    TZS(displayCode = "TZS", flag = "🇹🇿"), // Tanzanian Shilling
    UAH(displayCode = "UAH", flag = "🇺🇦"), // Ukrainian Hryvnia
    UGX(displayCode = "UGX", flag = "🇺🇬"), // Ugandan Shilling
    USD(displayCode = "USD", flag = "🇺🇸"), // United States Dollar
    UYU(displayCode = "UYU", flag = "🇺🇾"), // Uruguayan Peso
    UZS(displayCode = "UZS", flag = "🇺🇿"), // Uzbekistan Som
    VND(displayCode = "VND", flag = "🇻🇳"), // Vietnamese Dong
    VUV(displayCode = "VUV", flag = "🇻🇺"), // Vanuatu Vatu
    WST(displayCode = "WST", flag = "🇼🇸"), // Samoan Tala
    XAF(displayCode = "XAF", flag = "🇨🇲"), // CFA Franc BEAC
    XCD(displayCode = "XCD", flag = "🇱🇨"), // East Caribbean Dollar
    XOF(displayCode = "XOF", flag = "🇨🇮"), // CFA Franc BCEAO
    XPF(displayCode = "XPF", flag = "🇳🇨"), // CFP Franc
    YER(displayCode = "YER", flag = "🇾🇪"), // Yemeni Rial
    ZAR(displayCode = "ZAR", flag = "🇿🇦"), // South African Rand
    ZMW(displayCode = "ZMW", flag = "🇿🇲"); // Zambian Kwacha

    /** Returns a pair of string (short name, full name) for this [fiatCurrency]. */
    suspend fun getLabel(): Pair<String, String> {
        val fullName = when {
            // use the free market rates as default. Name for official rates gets a special tag, as those rates are usually inaccurate.
            this == ARS -> getString(Res.string.currency_ars_official)
            this == ARS_BM -> getString(Res.string.currency_ars_bm)
            this == CUP -> getString(Res.string.currency_cup_official)
            this == CUP_FM -> getString(Res.string.currency_cup_fm)
            this == LBP -> getString(Res.string.currency_lbp_official)
            this == LBP_BM -> getString(Res.string.currency_lbp_bm)
            // use the JVM API otherwise to get the name
            this.displayCode.length == 3 -> try {
                this.name
            } catch (e: Exception) {
                "N/A"
            }
            else -> "N/A"
        }
        return "${this.flag} ${this.displayCode}" to fullName
    }
    
    companion object {
        val values = entries
        fun valueOfOrNull(code: String): FiatCurrency? = try {
            valueOf(code)
        } catch (e: Exception) {
            null
        }
    }
}

sealed class ElectrumConfig {
    /**
     * Note : constructor is private because we want to enforce a disabled tls policy on onion hosts.
     *
     * @param requireOnionIfTorEnabled if this option is true, Phoenix will require this custom server to use an onion address when Tor is enabled, otherwise
     *          it will not connect to it. This parameter should be true in the normal case. However, the user may want to override this requirement, for
     *          example, if he's connecting to his own server and does not care about leaking his IP if the Tor proxy fails.
     */
    class Custom private constructor(val server: ServerAddress, val requireOnionIfTorEnabled: Boolean) : ElectrumConfig() {
        companion object {
            fun create(server: ServerAddress, requireOnionIfTorEnabled: Boolean) = Custom(
                server = if (server.isOnion) server.copy(tls = TcpSocket.TLS.DISABLED) else server,
                requireOnionIfTorEnabled = requireOnionIfTorEnabled
            )
        }
    }
    object Random : ElectrumConfig()

    override operator fun equals(other: Any?): Boolean {
        if (other !is ElectrumConfig) {
            return false
        }
        return when (this) {
            is Custom -> {
                when (other) {
                    is Custom -> this === other // custom =?= custom
                    is Random -> false          // custom != random
                }
            }
            is Random -> {
                when (other) {
                    is Custom -> false // random != custom
                    is Random -> true  // random == random
                }
            }
        }
    }
}

data class StartupParams(
    /** If true, we'll use onion addresses when connecting to the peer and to Electrum servers. */
    val isTorEnabled: Boolean,
    /** The liquidity policy must be injected into the node params manager. */
    val liquidityPolicy: LiquidityPolicy,
    // TODO: add custom electrum address, fiat currencies, ...
)

@Serializable
data class PreferredFiatCurrencies(
    val primary: FiatCurrency,
    val others: Set<FiatCurrency>
) {
    constructor(primary: FiatCurrency, others: List<FiatCurrency>) :
            this(primary = primary, others = others.toSet())

    val all: Set<FiatCurrency>
        get() {
            return if (others.contains(primary)) {
                others
            } else {
                others.toMutableSet().apply { add(primary) }
            }
        }
}
