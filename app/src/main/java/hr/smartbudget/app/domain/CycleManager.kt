package hr.smartbudget.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Obračunski ciklus [pocetak, kraj] (oba dana uključena).
 * [sidro] je mjesec u kojem ciklus po pravilu počinje; po njemu se ciklusi pomiču naprijed/natrag.
 */
data class Ciklus(val sidro: YearMonth, val pocetak: LocalDate, val kraj: LocalDate) {
    val brojDana: Int get() = (ChronoUnit.DAYS.between(pocetak, kraj) + 1).toInt()

    operator fun contains(datum: LocalDate) = !datum.isBefore(pocetak) && !datum.isAfter(kraj)

    /** Broj proteklih dana uključujući danas; 0 za budući, puni broj za prošli ciklus. */
    fun proteklihDana(danas: LocalDate): Int = when {
        danas.isBefore(pocetak) -> 0
        danas.isAfter(kraj) -> brojDana
        else -> (ChronoUnit.DAYS.between(pocetak, danas) + 1).toInt()
    }

    fun odMillis(zona: ZoneId = ZoneId.systemDefault()) = pocetak.atStartOfDay(zona).toInstant().toEpochMilli()
    fun doMillisIskljucivo(zona: ZoneId = ZoneId.systemDefault()) =
        kraj.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
}

/**
 * Računa početak i kraj ciklusa za zadani početni dan (1–31).
 *
 * Mjeseci s 28, 29, 30 ili 31 dan: ako mjesec nema odabrani dan, ciklus počinje zadnjeg dana
 * tog mjeseca ([zadnjiDanAkoNema] = true) ili prvog dana sljedećeg mjeseca (false).
 * Ciklusi su uvijek uzastopni, bez praznina i preklapanja.
 */
class CycleManager(pocetniDan: Int, private val zadnjiDanAkoNema: Boolean = true) {
    private val dan = pocetniDan.coerceIn(1, 31)

    /** Dan na koji počinje ciklus usidren u mjesecu [mjesec]. */
    fun pocetakZaMjesec(mjesec: YearMonth): LocalDate = when {
        dan <= mjesec.lengthOfMonth() -> mjesec.atDay(dan)
        zadnjiDanAkoNema -> mjesec.atEndOfMonth()
        else -> mjesec.plusMonths(1).atDay(1)
    }

    fun ciklusZaSidro(sidro: YearMonth): Ciklus =
        Ciklus(sidro, pocetakZaMjesec(sidro), pocetakZaMjesec(sidro.plusMonths(1)).minusDays(1))

    /** Ciklus koji sadrži [datum]. */
    fun ciklusZa(datum: LocalDate): Ciklus {
        var sidro = YearMonth.from(datum)
        if (datum.isBefore(pocetakZaMjesec(sidro))) sidro = sidro.minusMonths(1)
        return ciklusZaSidro(sidro)
    }

    /** Ciklus pomaknut za [pomak] u odnosu na onaj koji sadrži [danas] (−1 = prethodni, +1 = sljedeći). */
    fun ciklus(danas: LocalDate, pomak: Int): Ciklus =
        ciklusZaSidro(ciklusZa(danas).sidro.plusMonths(pomak.toLong()))
}
