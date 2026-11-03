package hr.smartbudget.app.domain

import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun TransakcijaSKategorijom.datum(zona: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(transakcija.datumVrijeme).atZone(zona).toLocalDate()

fun TransakcijaSKategorijom.vrijeme(zona: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(transakcija.datumVrijeme).atZone(zona).toLocalDateTime()

val TransakcijaSKategorijom.predznaceniCenti: Long
    get() = if (transakcija.tip == TipTransakcije.PRIHOD) transakcija.iznosCenti else -transakcija.iznosCenti

data class Zbroj(val prihodi: Long = 0, val troskovi: Long = 0) {
    val razlika get() = prihodi - troskovi
}

fun List<TransakcijaSKategorijom>.zbroj(): Zbroj = Zbroj(
    prihodi = filter { it.transakcija.tip == TipTransakcije.PRIHOD }.sumOf { it.transakcija.iznosCenti },
    troskovi = filter { it.transakcija.tip == TipTransakcije.TROSAK }.sumOf { it.transakcija.iznosCenti },
)

enum class StatusLimita { BEZ_LIMITA, SIGURNO, BLIZU, PREKORACENO }

/** Prag upozorenja (80 %) i prekoračenja (100 %) limita kategorije. */
const val PRAG_UPOZORENJA = 0.80

fun statusLimita(potroseno: Long, limit: Long?): StatusLimita = when {
    limit == null || limit <= 0 -> StatusLimita.BEZ_LIMITA
    potroseno >= limit -> StatusLimita.PREKORACENO
    potroseno >= limit * PRAG_UPOZORENJA -> StatusLimita.BLIZU
    else -> StatusLimita.SIGURNO
}

data class UdioKategorije(
    val kategorija: Kategorija,
    val centi: Long,
    /** 0–100 */
    val udio: Double,
    val status: StatusLimita,
    /** Iskorištenost limita u postocima, null ako nema limita. */
    val iskoristenost: Double?,
)

/** Raspodjela troškova: novčani i postotni udio svake kategorije u ukupnim troškovima ciklusa. */
fun raspodjelaTroskova(transakcije: List<TransakcijaSKategorijom>): List<UdioKategorije> =
    raspodjelaPoKategoriji(transakcije, TipTransakcije.TROSAK)

/** Novčani i postotni udio svake kategorije u ukupnim prihodima ili troškovima ([tip]). */
fun raspodjelaPoKategoriji(transakcije: List<TransakcijaSKategorijom>, tip: TipTransakcije): List<UdioKategorije> {
    val troskovi = transakcije.filter { it.transakcija.tip == tip }
    val ukupno = troskovi.sumOf { it.transakcija.iznosCenti }
    if (ukupno == 0L) return emptyList()
    return troskovi.groupBy { it.kategorija.id }.map { (_, lista) ->
        val k = lista.first().kategorija
        val c = lista.sumOf { it.transakcija.iznosCenti }
        UdioKategorije(
            kategorija = k,
            centi = c,
            udio = c * 100.0 / ukupno,
            status = statusLimita(c, k.mjesecniLimitCenti),
            iskoristenost = k.mjesecniLimitCenti?.takeIf { it > 0 }?.let { c * 100.0 / it },
        )
    }.sortedByDescending { it.centi }
}

/** Jedna točka kumulativnog tijeka novca (stanje na kraju dana). */
data class TockaTijeka(
    val datum: LocalDate,
    val prihodi: Long,
    val troskovi: Long,
    /** Stanje računa: početno stanje + neto do tog dana. */
    val stanje: Long,
) {
    val neto get() = prihodi - troskovi
}

/** Kumulativni tijek novca iz dana u dan, od početka ciklusa do [doDana] (uključivo). */
fun tijekNovca(
    ciklus: Ciklus,
    transakcije: List<TransakcijaSKategorijom>,
    pocetnoStanje: Long,
    doDana: LocalDate,
): List<TockaTijeka> {
    val zadnji = minOf(doDana, ciklus.kraj)
    if (zadnji.isBefore(ciklus.pocetak)) return emptyList()
    val poDanu = transakcije.groupBy { it.datum() }
    var p = 0L
    var t = 0L
    val tocke = mutableListOf<TockaTijeka>()
    var d = ciklus.pocetak
    while (!d.isAfter(zadnji)) {
        poDanu[d].orEmpty().forEach {
            if (it.transakcija.tip == TipTransakcije.PRIHOD) p += it.transakcija.iznosCenti else t += it.transakcija.iznosCenti
        }
        tocke += TockaTijeka(d, p, t, pocetnoStanje + p - t)
        d = d.plusDays(1)
    }
    return tocke
}

data class StavkaPovijesti(val ciklus: Ciklus, val zbroj: Zbroj)

/** Ukupni prihodi i rashodi po ciklusima (najstariji prvi). */
fun povijest(ciklusi: List<Ciklus>, transakcije: List<TransakcijaSKategorijom>): List<StavkaPovijesti> =
    ciklusi.map { c -> StavkaPovijesti(c, transakcije.filter { it.datum() in c }.zbroj()) }

data class PromjenaKategorije(val kategorija: Kategorija, val prije: Long, val sada: Long) {
    val promjenaPosto: Double? get() = if (prije > 0) (sada - prije) * 100.0 / prije else null
}

/** Kategorije s najvećom promjenom potrošnje u odnosu na prethodni ciklus (do istog dana ciklusa). */
fun najvecePromjene(
    sada: List<TransakcijaSKategorijom>,
    prije: List<TransakcijaSKategorijom>,
    koliko: Int = 3,
): List<PromjenaKategorije> {
    fun poKat(l: List<TransakcijaSKategorijom>) = l.filter { it.transakcija.tip == TipTransakcije.TROSAK }
        .groupBy { it.kategorija.id }
    val s = poKat(sada)
    val p = poKat(prije)
    return (s.keys + p.keys).map { id ->
        val k = (s[id] ?: p[id])!!.first().kategorija
        PromjenaKategorije(k, p[id].orEmpty().sumOf { it.transakcija.iznosCenti }, s[id].orEmpty().sumOf { it.transakcija.iznosCenti })
    }.sortedByDescending { kotlin.math.abs(it.sada - it.prije) }.take(koliko)
}
