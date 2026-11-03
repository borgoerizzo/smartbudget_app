package hr.smartbudget.app.domain

import hr.smartbudget.app.data.PonavljajuciUnos
import java.time.LocalDate
import java.time.YearMonth

/** Pravila mjesečnog ponavljanja (bez baze, radi testiranja). */
object Ponavljanje {
    /** Datum unosa u mjesecu: zadani dan ili zadnji dan mjeseca ako ga mjesec nema. */
    fun datumUMjesecu(dan: Int, mjesec: YearMonth): LocalDate = mjesec.atDay(minOf(dan, mjesec.lengthOfMonth()))

    /** Prvi mjesec koji još nije obrađen. */
    private fun prviMjesec(p: PonavljajuciUnos): YearMonth =
        p.zadnjiEpochDan?.let { YearMonth.from(LocalDate.ofEpochDay(it)).plusMonths(1) }
            ?: YearMonth.from(LocalDate.ofEpochDay(p.pocetakEpochDan))

    /** Svi datumi za koje unos treba stvoriti zaključno s [danas] (nadoknađuje i propuštene mjesece). */
    fun dospjeliDatumi(p: PonavljajuciUnos, danas: LocalDate): List<LocalDate> {
        if (!p.aktivan) return emptyList()
        val pocetak = LocalDate.ofEpochDay(p.pocetakEpochDan)
        val rezultat = mutableListOf<LocalDate>()
        var m = prviMjesec(p)
        while (true) {
            val d = datumUMjesecu(p.danUMjesecu, m)
            if (d.isAfter(danas)) break
            if (!d.isBefore(pocetak)) rezultat += d
            m = m.plusMonths(1)
        }
        return rezultat
    }

    /** Sljedeći datum na koji će se unos stvoriti. */
    fun sljedeciDatum(p: PonavljajuciUnos): LocalDate {
        val pocetak = LocalDate.ofEpochDay(p.pocetakEpochDan)
        var m = prviMjesec(p)
        while (true) {
            val d = datumUMjesecu(p.danUMjesecu, m)
            if (!d.isBefore(pocetak)) return d
            m = m.plusMonths(1)
        }
    }
}
