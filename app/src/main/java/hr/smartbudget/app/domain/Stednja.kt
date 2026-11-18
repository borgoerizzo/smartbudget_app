package hr.smartbudget.app.domain

import hr.smartbudget.app.data.Novcanik
import hr.smartbudget.app.data.PromjenaNovcanika
import hr.smartbudget.app.data.UplataCilja
import java.time.LocalDate
import java.time.YearMonth

/** Novčanik sa stanjem i poviješću promjena (najnovije prve). */
data class StanjeNovcanika(
    val novcanik: Novcanik,
    val valuta: Valuta,
    val stanje: Long,
    val promjene: List<PromjenaNovcanika>,
) {
    /** Uloženo u osnovnoj valuti; null ako ni jedna uplata nema upisan iznos kupnje. */
    val ulozeno: Long? get() = promjene.mapNotNull { it.ulozenoCenti }.takeIf { it.isNotEmpty() }?.sum()

    /** Svaka uplata ima upisan iznos kupnje (tek tada je dobit/gubitak točan). */
    val ulozenoPotpuno: Boolean get() = promjene.filter { it.jedinice > 0 }.all { it.ulozenoCenti != null }
}

fun stanjaNovcanika(novcanici: List<Novcanik>, promjene: List<PromjenaNovcanika>): List<StanjeNovcanika> {
    val po = promjene.groupBy { it.novcanikId }
    return novcanici.map { n ->
        val p = po[n.id].orEmpty().sortedWith(compareByDescending<PromjenaNovcanika> { it.epochDan }.thenByDescending { it.id })
        StanjeNovcanika(n, Valute.po(n.valuta), p.sumOf { it.jedinice }, p)
    }
}

/** Stanja cilja po stranim valutama (kod → najmanje jedinice), bez osnovne valute; samo valute s pozitivnim stanjem. */
fun valuteCilja(ciljId: Long, uplate: List<UplataCilja>): Map<String, Long> =
    uplate.filter { it.ciljId == ciljId && it.valuta != null }
        .groupBy { it.valuta!! }.mapValues { (_, l) -> l.sumOf { it.iznosCenti } }
        .filterValues { it > 0 }

/**
 * Ušteđeno na cilju u osnovnoj valuti (centi): uplate u osnovnoj valuti + strane valute po današnjem tečaju.
 * Valute bez tečaja se preskaču; [potpuno] kaže je li sve preračunato.
 */
data class UstedjenoCilja(val centi: Long, val potpuno: Boolean, val valute: Map<String, Long>)

fun ustedjenoCilja(c: hr.smartbudget.app.data.CiljStednje, uplate: List<UplataCilja>, uOsnovnu: (Long, Valuta) -> Double?): UstedjenoCilja {
    val valute = valuteCilja(c.id, uplate)
    var centi = c.ustedjenoCenti
    var potpuno = true
    valute.forEach { (kod, j) -> uOsnovnu(j, Valute.po(kod))?.let { centi += kotlin.math.round(it * 100).toLong() } ?: run { potpuno = false } }
    return UstedjenoCilja(centi, potpuno, valute)
}

/** Količina u novčaniku na kraju svakog dana razdoblja [od, doDana]. */
fun kolicinaPoDanima(promjene: List<PromjenaNovcanika>, od: LocalDate, doDana: LocalDate): List<Pair<LocalDate, Long>> {
    val prije = promjene.filter { it.epochDan < od.toEpochDay() }.sumOf { it.jedinice }
    val poDanu = promjene.filter { it.epochDan >= od.toEpochDay() }.groupBy { it.epochDan }.mapValues { (_, l) -> l.sumOf { it.jedinice } }
    var stanje = prije
    val rez = mutableListOf<Pair<LocalDate, Long>>()
    var d = od
    while (!d.isAfter(doDana)) {
        stanje += poDanu[d.toEpochDay()] ?: 0L
        rez += d to stanje
        d = d.plusDays(1)
    }
    return rez
}

/**
 * Vrijednost kripto portfelja u eurima po danima: količina na kraju dana × cijena tog dana.
 * [cijene] su dnevne cijene po kodu valute; dani bez cijene uzimaju zadnju poznatu.
 */
fun vrijednostPortfelja(
    novcanici: List<StanjeNovcanika>,
    cijene: Map<String, List<Pair<LocalDate, Double>>>,
    od: LocalDate,
    doDana: LocalDate,
): List<Pair<LocalDate, Double>> {
    val kripto = novcanici.filter { it.valuta.kripto && cijene[it.valuta.kod] != null }
    if (kripto.isEmpty() || doDana.isBefore(od)) return emptyList()
    val kolicine = kripto.associate { it.novcanik.id to kolicinaPoDanima(it.promjene, od, doDana).toMap() }
    val cijeneMape = kripto.associate { it.valuta.kod to cijene.getValue(it.valuta.kod).toMap() }
    val zadnja = mutableMapOf<String, Double>()
    val rez = mutableListOf<Pair<LocalDate, Double>>()
    var d = od
    while (!d.isAfter(doDana)) {
        var ukupno = 0.0
        kripto.forEach { s ->
            val kod = s.valuta.kod
            cijeneMape[kod]?.get(d)?.let { zadnja[kod] = it }
            val c = zadnja[kod] ?: cijene.getValue(kod).firstOrNull()?.second ?: 0.0
            ukupno += uBroj(kolicine.getValue(s.novcanik.id)[d] ?: 0L, s.valuta) * c
        }
        rez += d to ukupno
        d = d.plusDays(1)
    }
    return rez
}

private fun uBroj(jedinice: Long, v: Valuta) = Valute.uBroj(jedinice, v)

/**
 * Neto uplate u štednju po mjesecima (zadnjih [mjeseci], najstariji prvi), u osnovnoj valuti.
 * Uplate na ciljeve su već u osnovnoj valuti; promjene u novčanicima preračunava [uOsnovnu] (null = nema tečaja, preskače se).
 */
fun uplatePoMjesecima(
    uplateCilja: List<UplataCilja>,
    novcanici: List<StanjeNovcanika>,
    uOsnovnu: (Long, Valuta) -> Double?,
    mjeseci: Int = 6,
    danas: LocalDate = LocalDate.now(),
): List<Pair<YearMonth, Double>> {
    val zadnji = YearMonth.from(danas)
    val raspon = (mjeseci - 1 downTo 0).map { zadnji.minusMonths(it.toLong()) }
    val zbroj = raspon.associateWith { 0.0 }.toMutableMap()
    uplateCilja.forEach { u ->
        val m = YearMonth.from(LocalDate.ofEpochDay(u.epochDan))
        val iznos = if (u.valuta == null) u.iznosCenti / 100.0 else uOsnovnu(u.iznosCenti, Valute.po(u.valuta))
        if (m in zbroj && iznos != null) zbroj[m] = zbroj.getValue(m) + iznos
    }
    novcanici.forEach { s ->
        s.promjene.forEach { p ->
            val m = YearMonth.from(LocalDate.ofEpochDay(p.epochDan))
            // Kupnja s upisanim iznosom računa se po plaćenom, ostalo po današnjem tečaju.
            val iznos = p.ulozenoCenti?.takeIf { p.jedinice > 0 }?.let { it / 100.0 } ?: uOsnovnu(p.jedinice, s.valuta)
            if (m in zbroj) iznos?.let { zbroj[m] = zbroj.getValue(m) + it }
        }
    }
    return raspon.map { it to zbroj.getValue(it) }
}
