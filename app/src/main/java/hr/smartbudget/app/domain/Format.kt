package hr.smartbudget.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Formatiranje iznosa po hr-HR: 1.627,40 €, minus kao −, plus kao +. */
object Novac {
    private val simboli = DecimalFormatSymbols(Locale.forLanguageTag("hr-HR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private fun fmt() = DecimalFormat("#,##0.00", simboli)
    private fun fmtCijeli() = DecimalFormat("#,##0", simboli)

    const val MINUS = "−"

    /** Oznaka osnovne valute aplikacije (postavlja se iz Postavki); iznosi se ne preračunavaju. */
    @Volatile var simbol: String = "€"
    @Volatile var kodValute: String = "EUR"

    fun eur(centi: Long): String = fmt().format(BigDecimal.valueOf(centi, 2)) + " " + simbol

    /** S predznakom: +1.250,00 € ili −45,80 €. */
    fun predznak(centi: Long): String = when {
        centi > 0 -> "+" + eur(centi)
        centi < 0 -> MINUS + eur(-centi)
        else -> eur(0)
    }

    /** Apsolutna vrijednost s minusom ako je negativna: −12,00 € / 12,00 €. */
    fun stanje(centi: Long): String = if (centi < 0) MINUS + eur(-centi) else eur(centi)

    fun broj(centi: Long): String = fmt().format(BigDecimal.valueOf(centi, 2))
    fun cijeli(eura: Long): String = fmtCijeli().format(eura)

    /** Parsira korisnički unos ("1.250,5", "45.80", "12") u cente; null ako nije ispravno. */
    fun parsiraj(unos: String): Long? {
        var s = unos.trim().replace(" ", "").replace("€", "").replace(simbol, "")
        if (s.isEmpty()) return null
        val zadnjiZarez = s.lastIndexOf(',')
        val zadnjaTocka = s.lastIndexOf('.')
        s = when {
            zadnjiZarez >= 0 && zadnjaTocka >= 0 ->
                if (zadnjiZarez > zadnjaTocka) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            zadnjiZarez >= 0 -> s.replace(',', '.')
            // Samo točke: "1.250" (tisućice) ili "45.80" (decimale).
            zadnjaTocka >= 0 && s.count { it == '.' } == 1 && s.length - zadnjaTocka - 1 <= 2 -> s
            else -> s.replace(".", "")
        }
        return s.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.unscaledValue()?.toLong()
    }

    /** Za polje unosa: 1250,00 (bez tisućica). */
    fun zaUnos(centi: Long): String = BigDecimal.valueOf(centi, 2).toPlainString().replace('.', ',')

    /** Postotak s nedjeljivim razmakom, da se znak % ne prelomi u novi red. */
    fun postotak(v: Double, decimala: Int = 1): String =
        String.format(Locale.forLanguageTag("hr-HR"), "%.${decimala}f %%", v).replace('.', ',')
}

object HrDatum {
    val mjeseciKratko = listOf("sij", "velj", "ožu", "tra", "svi", "lip", "srp", "kol", "ruj", "lis", "stu", "pro")
    val mjeseci = listOf(
        "siječanj", "veljača", "ožujak", "travanj", "svibanj", "lipanj",
        "srpanj", "kolovoz", "rujan", "listopad", "studeni", "prosinac",
    )
    private val daniKratko = mapOf(
        DayOfWeek.MONDAY to "pon", DayOfWeek.TUESDAY to "uto", DayOfWeek.WEDNESDAY to "sri",
        DayOfWeek.THURSDAY to "čet", DayOfWeek.FRIDAY to "pet", DayOfWeek.SATURDAY to "sub", DayOfWeek.SUNDAY to "ned",
    )
    private val dani = mapOf(
        DayOfWeek.MONDAY to "Ponedjeljak", DayOfWeek.TUESDAY to "Utorak", DayOfWeek.WEDNESDAY to "Srijeda",
        DayOfWeek.THURSDAY to "Četvrtak", DayOfWeek.FRIDAY to "Petak", DayOfWeek.SATURDAY to "Subota", DayOfWeek.SUNDAY to "Nedjelja",
    )

    /** "3. stu" */
    fun kratko(d: LocalDate) = "${d.dayOfMonth}. ${mjeseciKratko[d.monthValue - 1]}"

    /** "uto, 3. stu" */
    fun sDanom(d: LocalDate) = "${daniKratko[d.dayOfWeek]}, ${kratko(d)}"

    /** "Danas", "Jučer" ili puni naziv dana. */
    fun naslovDana(d: LocalDate, danas: LocalDate): String = when (d) {
        danas -> "Danas"
        danas.minusDays(1) -> "Jučer"
        else -> dani.getValue(d.dayOfWeek)
    }

    /** "03.11.2026." */
    fun puni(d: LocalDate) = "%02d.%02d.%04d.".format(d.dayOfMonth, d.monthValue, d.year)

    /** "15. 10. 2026." (kao u dizajnu polja Od/Do) */
    fun srednji(d: LocalDate) = "${d.dayOfMonth}. ${d.monthValue}. ${d.year}."

    fun vrijeme(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

    /** "15. lis – 14. stu" */
    fun raspon(c: Ciklus) = "${kratko(c.pocetak)} – ${kratko(c.kraj)}"
}
