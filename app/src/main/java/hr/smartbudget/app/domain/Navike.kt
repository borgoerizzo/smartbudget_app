package hr.smartbudget.app.domain

import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Analize navika potrošnje (prijenosi između računa se ne broje). */

private fun List<TransakcijaSKategorijom>.stvarniTroskovi() =
    filter { it.jeStvarna && it.transakcija.tip == TipTransakcije.TROSAK }

/** Rashodi po danu ciklusa za kalendar potrošnje (svaki dan ciklusa, i oni bez troška). */
fun kalendarPotrosnje(ciklus: Ciklus, transakcije: List<TransakcijaSKategorijom>): List<Pair<LocalDate, Long>> {
    val poDanu = transakcije.stvarniTroskovi().groupBy { it.datum() }.mapValues { (_, l) -> l.sumOf { it.transakcija.iznosCenti } }
    return (0 until ciklus.brojDana).map { i -> ciklus.pocetak.plusDays(i.toLong()).let { d -> d to (poDanu[d] ?: 0L) } }
}

/**
 * Prosječni rashod po danu u tjednu (indeks 0 = ponedjeljak) u razdoblju [od, doDana].
 * Prosjek dijeli s brojem tih dana u razdoblju, ne samo s danima u kojima je bilo troška.
 */
fun prosjekPoDanuUTjednu(transakcije: List<TransakcijaSKategorijom>, od: LocalDate, doDana: LocalDate): List<Long> {
    if (doDana.isBefore(od)) return List(7) { 0L }
    val broj = IntArray(7)
    var d = od
    while (!d.isAfter(doDana)) { broj[d.dayOfWeek.value - 1]++; d = d.plusDays(1) }
    val zbroj = LongArray(7)
    transakcije.stvarniTroskovi().forEach {
        val dt = it.datum()
        if (!dt.isBefore(od) && !dt.isAfter(doDana)) zbroj[dt.dayOfWeek.value - 1] += it.transakcija.iznosCenti
    }
    return (0..6).map { if (broj[it] == 0) 0L else zbroj[it] / broj[it] }
}

data class FiksniPromjenjivi(val fiksni: Long, val promjenjivi: Long, val najveciFiksni: List<TransakcijaSKategorijom>) {
    val ukupno get() = fiksni + promjenjivi
    val udioFiksnih: Double get() = if (ukupno > 0) fiksni * 100.0 / ukupno else 0.0
}

/** Fiksni = rashodi nastali iz ponavljajućih unosa (krediti, pretplate, režije); ostalo je promjenjivo. */
fun fiksniPromjenjivi(transakcije: List<TransakcijaSKategorijom>): FiksniPromjenjivi {
    val (fiksni, promjenjivi) = transakcije.stvarniTroskovi().partition { it.transakcija.ponavljajuciId != null }
    return FiksniPromjenjivi(
        fiksni.sumOf { it.transakcija.iznosCenti },
        promjenjivi.sumOf { it.transakcija.iznosCenti },
        fiksni.sortedByDescending { it.transakcija.iznosCenti }.take(4),
    )
}

/** Stopa štednje: koliki dio prihoda je ostao (negativna = minus); null bez prihoda. */
fun stopaStednje(z: Zbroj): Double? = if (z.prihodi > 0) (z.prihodi - z.troskovi) * 100.0 / z.prihodi else null

data class NazivStatistika(val naziv: String, val broj: Int, val ukupno: Long) {
    val prosjek: Long get() = if (broj > 0) ukupno / broj else 0
}

/** Najčešći nazivi troškova (trgovine, usluge) po broju kupnji; isti naziv bez obzira na velika slova i kvačice. */
fun najcesciNazivi(transakcije: List<TransakcijaSKategorijom>, koliko: Int = 5): List<NazivStatistika> =
    transakcije.stvarniTroskovi()
        .filter { it.transakcija.opis.isNotBlank() }
        .groupBy { Pretraga.normaliziraj(it.transakcija.opis) }
        .map { (_, l) ->
            // Prikazuje se najčešći oblik zapisa naziva
            val naziv = l.groupingBy { it.transakcija.opis.trim() }.eachCount().maxBy { it.value }.key
            NazivStatistika(naziv, l.size, l.sumOf { it.transakcija.iznosCenti })
        }
        .filter { it.broj >= 2 }
        .sortedWith(compareByDescending<NazivStatistika> { it.broj }.thenByDescending { it.ukupno })
        .take(koliko)

/** Broj dana u razdoblju (uključivo), za opise. */
fun brojDana(od: LocalDate, doDana: LocalDate): Long = ChronoUnit.DAYS.between(od, doDana) + 1
