package hr.smartbudget.app.domain

import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Godišnji pregled ---

data class GodisnjiPregled(
    val godina: Int,
    /** Zbroj po kalendarskim mjesecima (indeks 0 = siječanj). */
    val poMjesecima: List<Zbroj>,
    val ukupno: Zbroj,
    val najveciTroskovi: List<TransakcijaSKategorijom>,
    val poKategorijama: List<UdioKategorije>,
    /** Broj mjeseci s unosima (za prosjek). */
    val mjeseciSUnosima: Int,
    val dostupneGodine: List<Int>,
)

fun godisnjiPregled(sve: List<TransakcijaSKategorijom>, godina: Int): GodisnjiPregled {
    val uGodini = sve.filter { it.datum().year == godina }
    val poMj = (1..12).map { m -> uGodini.filter { it.datum().monthValue == m }.zbroj() }
    return GodisnjiPregled(
        godina = godina,
        poMjesecima = poMj,
        ukupno = uGodini.zbroj(),
        najveciTroskovi = uGodini.filter { it.jeStvarna && it.transakcija.tip == TipTransakcije.TROSAK }
            .sortedByDescending { it.transakcija.iznosCenti }.take(5),
        poKategorijama = raspodjelaPoKategoriji(uGodini, TipTransakcije.TROSAK),
        mjeseciSUnosima = poMj.count { it.prihodi > 0 || it.troskovi > 0 },
        dostupneGodine = (sve.map { it.datum().year } + LocalDate.now().year).distinct().sorted(),
    )
}

// --- Prognoza limita ---

/** Procjena potrošnje do kraja ciklusa uz dosadašnji dnevni tempo. */
fun prognoza(potroseno: Long, protekloDana: Int, brojDana: Int): Long =
    if (protekloDana <= 0) potroseno else potroseno * brojDana / protekloDana

/** Prognoza prelazi limit, a limit još nije dosegnut (rano upozorenje). */
fun prognozaPrekoracenja(potroseno: Long, limit: Long?, protekloDana: Int, brojDana: Int): Boolean =
    limit != null && limit > 0 && potroseno < limit && protekloDana >= 3 && prognoza(potroseno, protekloDana, brojDana) > limit

// --- Prijedlozi pri unosu ---

/** Prethodni unosi čiji naziv sadrži upisani tekst (jedan po nazivu, najnoviji prvi). */
fun prijedloziZaNaziv(zadnje: List<TransakcijaSKategorijom>, upit: String, tip: TipTransakcije, koliko: Int = 4): List<TransakcijaSKategorijom> {
    val q = Pretraga.normaliziraj(upit)
    if (q.isEmpty()) return emptyList()
    return zadnje.asSequence()
        .filter { it.transakcija.tip == tip && it.jeStvarna }
        .filter { Pretraga.normaliziraj(it.transakcija.opis).contains(q) }
        .distinctBy { Pretraga.normaliziraj(it.transakcija.opis) }
        .filter { Pretraga.normaliziraj(it.transakcija.opis) != q }
        .take(koliko).toList()
}

/**
 * Zadnji različiti nazivi unosa u kategoriji (najnoviji prvi), za brzi odabir čim se kategorija odabere.
 * [zadnje] mora biti poredan od najnovijeg; prazni nazivi i prijenosi se preskaču.
 */
fun zadnjiNaziviKategorije(zadnje: List<TransakcijaSKategorijom>, kategorijaId: Long, koliko: Int = 3): List<String> =
    zadnje.asSequence()
        .filter { it.jeStvarna && it.kategorija.id == kategorijaId && it.transakcija.opis.isNotBlank() }
        .map { it.transakcija.opis.trim() }
        .distinctBy { Pretraga.normaliziraj(it) }
        .take(koliko).toList()

// --- Uvoz izvoda (CSV) ---

data class CsvTablica(val zaglavlje: List<String>, val redovi: List<List<String>>)

object UvozCsv {
    /** Parsira CSV (separator ; , ili tab; navodnici), prvi redak je zaglavlje. */
    fun parsiraj(tekst: String): CsvTablica {
        val cist = tekst.removePrefix("﻿")
        val prviRed = cist.lineSequence().firstOrNull { it.isNotBlank() } ?: return CsvTablica(emptyList(), emptyList())
        val sep = listOf(';', '\t', ',').maxBy { s -> prviRed.count { it == s } }
        val redovi = mutableListOf<List<String>>()
        var polje = StringBuilder()
        var red = mutableListOf<String>()
        var uNavodnicima = false
        var i = 0
        while (i < cist.length) {
            val c = cist[i]
            when {
                uNavodnicima && c == '"' && i + 1 < cist.length && cist[i + 1] == '"' -> { polje.append('"'); i++ }
                c == '"' -> uNavodnicima = !uNavodnicima
                !uNavodnicima && c == sep -> { red += polje.toString().trim(); polje = StringBuilder() }
                !uNavodnicima && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < cist.length && cist[i + 1] == '\n') i++
                    red += polje.toString().trim(); polje = StringBuilder()
                    if (red.any { it.isNotEmpty() }) redovi += red
                    red = mutableListOf()
                }
                else -> polje.append(c)
            }
            i++
        }
        red += polje.toString().trim()
        if (red.any { it.isNotEmpty() }) redovi += red
        if (redovi.isEmpty()) return CsvTablica(emptyList(), emptyList())
        return CsvTablica(redovi.first(), redovi.drop(1))
    }

    private val formati = listOf("d.M.yyyy.", "d.M.yyyy", "dd.MM.yyyy.", "dd.MM.yyyy", "yyyy-MM-dd", "d/M/yyyy", "dd/MM/yyyy", "d.M.yy", "dd.MM.yy")
        .map { DateTimeFormatter.ofPattern(it) }

    /** Datum u uobičajenim formatima (15.10.2026., 2026-10-15, 15/10/2026...); vrijeme iza datuma se zanemaruje. */
    fun datum(s: String): LocalDate? {
        val d = s.trim().split(' ', 'T').first()
        return formati.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(d, f) }.getOrNull() }
    }

    /** Iznos s predznakom u centima ("-45,80", "1.250,00", "−12.5"). */
    fun iznos(s: String): Long? = Novac.parsiraj(s.replace('−', '-'))

    /** Pogađa stupce po nazivima u zaglavlju: (datum, iznos, opis). */
    fun pogodiStupce(zaglavlje: List<String>): Triple<Int, Int, Int> {
        fun nadji(vararg kljucevi: String) = zaglavlje.indexOfFirst { h -> val n = Pretraga.normaliziraj(h); kljucevi.any { n.contains(it) } }
        val datum = nadji("datum", "date", "valuta").coerceAtLeast(0)
        val iznos = nadji("iznos", "amount", "promet", "suma").let { if (it < 0) 1.coerceAtMost(zaglavlje.lastIndex) else it }
        val opis = nadji("opis", "description", "primatelj", "platitelj", "naziv", "svrha").let { if (it < 0) 2.coerceAtMost(zaglavlje.lastIndex) else it }
        return Triple(datum, iznos, opis)
    }
}

// --- Tjedni pregled ---

data class DanTjedna(val datum: LocalDate, val prihodi: Long, val troskovi: Long)

data class TjedniSazetak(
    val od: LocalDate,
    val doDana: LocalDate,
    val zbroj: Zbroj,
    val prethodni: Zbroj,
    val poDanima: List<DanTjedna>,
    val kategorije: List<UdioKategorije>,
    val najveciTrosak: TransakcijaSKategorijom?,
    val brojUnosa: Int,
) {
    /** Promjena rashoda u odnosu na prethodnih 7 dana (%), null ako prethodni tjedan nema rashoda. */
    val promjenaRashoda: Double? get() = if (prethodni.troskovi > 0) (zbroj.troskovi - prethodni.troskovi) * 100.0 / prethodni.troskovi else null
    val prosjekDnevno: Long get() = zbroj.troskovi / 7
}

/** Zadnjih 7 dana zaključno s [danas] i usporedba s 7 dana prije toga (prijenosi se ne broje). */
fun tjedniSazetak(sve: List<TransakcijaSKategorijom>, danas: LocalDate): TjedniSazetak {
    val od = danas.minusDays(6)
    val stvarne = sve.filter { it.jeStvarna }
    val tjedan = stvarne.filter { val d = it.datum(); !d.isBefore(od) && !d.isAfter(danas) }
    val prije = stvarne.filter { val d = it.datum(); !d.isBefore(od.minusDays(7)) && d.isBefore(od) }
    return TjedniSazetak(
        od = od,
        doDana = danas,
        zbroj = tjedan.zbroj(),
        prethodni = prije.zbroj(),
        poDanima = (0..6).map { i ->
            val d = od.plusDays(i.toLong())
            val z = tjedan.filter { it.datum() == d }.zbroj()
            DanTjedna(d, z.prihodi, z.troskovi)
        },
        kategorije = raspodjelaPoKategoriji(tjedan, TipTransakcije.TROSAK),
        najveciTrosak = tjedan.filter { it.transakcija.tip == TipTransakcije.TROSAK }.maxByOrNull { it.transakcija.iznosCenti },
        brojUnosa = tjedan.size,
    )
}
