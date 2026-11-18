package hr.smartbudget.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Valuta novčanika ili aplikacije. Iznosi se čuvaju kao cijeli broj najmanjih jedinica:
 * fiat u centima (2 decimale), kriptovalute u stomilijuntinkama (8 decimala).
 */
data class Valuta(
    val kod: String,
    val naziv: String,
    val simbol: String,
    val kripto: Boolean = false,
    /** Oznaka na CoinGeckou (samo kriptovalute). */
    val coingeckoId: String? = null,
) {
    val decimale: Int get() = if (kripto) 8 else 2
}

object Valute {
    /** Najčešće korištene u Hrvatskoj; sve su na tečajnoj listi HNB-a. */
    val fiat = listOf(
        Valuta("EUR", "Euro", "€"),
        Valuta("USD", "Američki dolar", "$"),
        Valuta("GBP", "Britanska funta", "£"),
        Valuta("CHF", "Švicarski franak", "CHF"),
        Valuta("CAD", "Kanadski dolar", "CAD"),
        Valuta("AUD", "Australski dolar", "AUD"),
        Valuta("BAM", "Konvertibilna marka", "KM"),
        Valuta("PLN", "Poljski zlot", "zł"),
        Valuta("CZK", "Češka kruna", "Kč"),
        Valuta("HUF", "Mađarska forinta", "Ft"),
        Valuta("SEK", "Švedska kruna", "SEK"),
        Valuta("NOK", "Norveška kruna", "NOK"),
        Valuta("DKK", "Danska kruna", "DKK"),
        Valuta("JPY", "Japanski jen", "¥"),
    )

    val kripto = listOf(
        Valuta("BTC", "Bitcoin", "BTC", true, "bitcoin"),
        Valuta("ETH", "Ethereum", "ETH", true, "ethereum"),
        Valuta("USDT", "Tether", "USDT", true, "tether"),
        Valuta("USDC", "USD Coin", "USDC", true, "usd-coin"),
        Valuta("SOL", "Solana", "SOL", true, "solana"),
        Valuta("XRP", "XRP", "XRP", true, "ripple"),
    )

    val sve = fiat + kripto

    fun po(kod: String): Valuta = sve.firstOrNull { it.kod == kod } ?: Valuta(kod, kod, kod)

    private val simboli = DecimalFormatSymbols(Locale.forLanguageTag("hr-HR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    /** Iznos u najmanjim jedinicama s oznakom valute: "100,00 $", "0,0525 BTC". */
    fun iznos(jedinice: Long, v: Valuta): String {
        val f = if (v.kripto) DecimalFormat("#,##0.00######", simboli) else DecimalFormat("#,##0.00", simboli)
        val tekst = f.format(BigDecimal.valueOf(kotlin.math.abs(jedinice), v.decimale))
        return (if (jedinice < 0) Novac.MINUS else "") + tekst + " " + v.simbol
    }

    /** Za polje unosa s desna: svih [decimale] decimala, s tisućicama ("0,00500000"). */
    fun iznosBezOznake(jedinice: Long, decimale: Int): String =
        DecimalFormat("#,##0." + "0".repeat(decimale), simboli).format(BigDecimal.valueOf(jedinice, decimale))

    /** Decimalni iznos (npr. preračunat) s oznakom valute. */
    fun iznos(vrijednost: Double, v: Valuta): String =
        iznos(BigDecimal.valueOf(vrijednost).setScale(v.decimale, RoundingMode.HALF_UP).unscaledValue().toLong(), v)

    fun uBroj(jedinice: Long, v: Valuta): Double = BigDecimal.valueOf(jedinice, v.decimale).toDouble()

    /** Parsira unos ("0,05", "1.250,5", "0.00012") u najmanje jedinice; null ako nije ispravno ili je previše decimala. */
    fun parsiraj(unos: String, v: Valuta): Long? {
        if (!v.kripto) return Novac.parsiraj(unos)
        var s = unos.trim().replace(" ", "")
        if (s.isEmpty()) return null
        // Kod kriptovaluta su decimale česte: i zarez i točka znače decimalni separator ako je samo jedan.
        s = when {
            s.count { it == ',' } == 1 && '.' !in s -> s.replace(',', '.')
            s.count { it == '.' } == 1 && ',' !in s -> s
            s.lastIndexOf(',') > s.lastIndexOf('.') -> s.replace(".", "").replace(',', '.')
            else -> s.replace(",", "")
        }
        val bd = s.toBigDecimalOrNull() ?: return null
        if (bd.scale() > v.decimale) return null
        return bd.setScale(v.decimale, RoundingMode.UNNECESSARY).unscaledValue().toLong()
    }

    /** Za polje unosa (bez tisućica, s decimalnim zarezom). */
    fun zaUnos(jedinice: Long, v: Valuta): String =
        BigDecimal.valueOf(jedinice, v.decimale).stripTrailingZeros().toPlainString().replace('.', ',')
}
