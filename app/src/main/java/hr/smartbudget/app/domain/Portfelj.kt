package hr.smartbudget.app.domain

import hr.smartbudget.app.data.Pozicija
import hr.smartbudget.app.data.PromjenaPozicije
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/** Ulaganje sa stanjem (količina) i poviješću kupnji i prodaja (najnovije prve). */
data class StanjePozicije(
    val pozicija: Pozicija,
    val kolicinaMikro: Long,
    val promjene: List<PromjenaPozicije>,
) {
    val kolicina: Double get() = Portfelj.uBroj(kolicinaMikro)

    /** Uloženo u osnovnoj valuti (centi); null ako ni jedna kupnja nema upisan iznos. */
    val ulozeno: Long? get() = promjene.mapNotNull { it.ulozenoCenti }.takeIf { it.isNotEmpty() }?.sum()

    /** Svaka kupnja ima upisan iznos (tek tada je dobit/gubitak točan). */
    val ulozenoPotpuno: Boolean get() = promjene.filter { it.kolicinaMikro > 0 }.all { it.ulozenoCenti != null }

    /** Dan kupnje (najstarija promjena). */
    val kupljenoEpochDan: Long? get() = promjene.minOfOrNull { it.epochDan }

    /** Ručno uneseni zapis ili obveznica: vrijednost na dan u valuti zapisa; null za tržišna ulaganja. */
    fun vrijednostRucno(dan: Long): Double? {
        val dosp = pozicija.dospijeceEpochDan ?: return null
        val nominal = (pozicija.nominalCenti ?: return null) / 100.0
        val ul = (ulozeno ?: return null) / 100.0
        return Portfelj.vrijednostZapisa(ul, nominal, kupljenoEpochDan ?: dan, dosp, dan)
    }

    /** Godišnji prinos ručnog zapisa u postocima (jednostavna kamata, stvarni broj dana / 365). */
    val prinosGodisnji: Double?
        get() {
            val ul = ulozeno ?: return null
            val nominal = pozicija.nominalCenti ?: return null
            return Portfelj.prinosGodisnji(ul / 100.0, nominal / 100.0, kupljenoEpochDan ?: return null, pozicija.dospijeceEpochDan ?: return null)
        }
}

object Portfelj {
    /** Količina se čuva u milijuntinkama (6 decimala), jer se kupuju i dijelovi dionica. */
    const val DECIMALE = 6

    fun stanja(pozicije: List<Pozicija>, promjene: List<PromjenaPozicije>): List<StanjePozicije> {
        val po = promjene.groupBy { it.pozicijaId }
        return pozicije.map { p ->
            val l = po[p.id].orEmpty().sortedWith(compareByDescending<PromjenaPozicije> { it.epochDan }.thenByDescending { it.id })
            StanjePozicije(p, l.sumOf { it.kolicinaMikro }, l)
        }
    }

    fun uBroj(mikro: Long): Double = BigDecimal.valueOf(mikro, DECIMALE).toDouble()

    private val simboli = DecimalFormatSymbols(Locale.forLanguageTag("hr-HR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    /** "12", "0,5", "1.250,125" (bez suvišnih nula). */
    fun kolicina(mikro: Long): String = DecimalFormat("#,##0.######", simboli).format(BigDecimal.valueOf(mikro, DECIMALE))

    /** Unos količine ("1,5", "0.25", "1.000") u milijuntinke; null ako nije ispravno ili ima više od 6 decimala. */
    fun parsirajKolicinu(unos: String): Long? {
        var s = unos.trim().replace(" ", "")
        if (s.isEmpty()) return null
        s = when {
            s.count { it == ',' } == 1 && '.' !in s -> s.replace(',', '.')
            // Samo jedna točka: decimalni separator (kao kod kriptovaluta), npr. "0.25".
            s.count { it == '.' } == 1 && ',' !in s -> s
            s.lastIndexOf(',') > s.lastIndexOf('.') -> s.replace(".", "").replace(',', '.')
            else -> s.replace(",", "")
        }
        val bd = s.toBigDecimalOrNull() ?: return null
        if (bd.scale() > DECIMALE || bd.signum() < 0) return null
        return bd.setScale(DECIMALE, RoundingMode.UNNECESSARY).unscaledValue().toLong()
    }

    /**
     * Vrijednost diskontnog zapisa na dan: od uloženog (dan kupnje) linearno raste do iznosa pri dospijeću,
     * koji dosegne na dan dospijeća (i ostaje). Prije kupnje 0.
     */
    fun vrijednostZapisa(ulozeno: Double, nominal: Double, kupljeno: Long, dospijece: Long, dan: Long): Double = when {
        dan < kupljeno -> 0.0
        dan >= dospijece || dospijece <= kupljeno -> nominal
        else -> ulozeno + (nominal - ulozeno) * (dan - kupljeno).toDouble() / (dospijece - kupljeno)
    }

    /** Godišnji prinos u postocima: (iznos pri dospijeću / uloženo − 1) × 365 / broj dana. */
    fun prinosGodisnji(ulozeno: Double, nominal: Double, kupljeno: Long, dospijece: Long): Double? =
        if (ulozeno > 0 && dospijece > kupljeno) (nominal / ulozeno - 1) * 365.0 / (dospijece - kupljeno) * 100 else null

    /**
     * Valuta kotacije svedena na valutu s tečajne liste: neke burze (London) kotiraju u penijima (GBp),
     * pa se cijena dijeli sa 100. Vraća (valuta, faktor).
     */
    fun normalizirajValutu(valuta: String): Pair<String, Double> = when (valuta) {
        "GBp", "GBX" -> "GBP" to 0.01
        "ZAc" -> "ZAR" to 0.01
        "ILA" -> "ILS" to 0.01
        else -> valuta.uppercase() to 1.0
    }

    /**
     * Vrijednost trenutnih količina po cijenama kroz razdoblje (kao kod kriptovaluta: kretanje tržišta
     * bez skokova od kupnji). [cijene] su dnevne cijene po simbolu, [uOsnovnu] preračunava 1 jedinicu valute
     * kotacije u osnovnu valutu. Dani bez cijene (vikend, praznik) uzimaju zadnju poznatu.
     */
    fun vrijednostPoDanima(
        stanja: List<StanjePozicije>,
        cijene: Map<String, List<Pair<LocalDate, Double>>>,
        uOsnovnu: (String) -> Double?,
        od: LocalDate,
        doDana: LocalDate,
    ): List<Pair<LocalDate, Double>> {
        val rucni = stanja.filter { it.pozicija.rucno && it.kolicinaMikro > 0 }
        val s = stanja.filter { !it.pozicija.rucno && it.kolicinaMikro > 0 && !cijene[it.pozicija.simbol].isNullOrEmpty() }
        if ((s.isEmpty() && rucni.isEmpty()) || doDana.isBefore(od)) return emptyList()
        val faktoriRucnih = rucni.associate { it.pozicija.id to (uOsnovnu(it.pozicija.valuta) ?: return emptyList()) }
        val faktori = s.associate { it.pozicija.simbol to (uOsnovnu(it.pozicija.valuta) ?: return emptyList()) }
        val mape = s.associate { it.pozicija.simbol to cijene.getValue(it.pozicija.simbol).toMap() }
        val zadnja = s.associate { st ->
            // Početna vrijednost: zadnja cijena prije razdoblja ili prva u razdoblju.
            val l = cijene.getValue(st.pozicija.simbol)
            st.pozicija.simbol to (l.lastOrNull { !it.first.isAfter(od) }?.second ?: l.first().second)
        }.toMutableMap()
        val rez = mutableListOf<Pair<LocalDate, Double>>()
        var d = od
        while (!d.isAfter(doDana)) {
            var ukupno = 0.0
            s.forEach { st ->
                val sim = st.pozicija.simbol
                mape.getValue(sim)[d]?.let { zadnja[sim] = it }
                ukupno += st.kolicina * zadnja.getValue(sim) * faktori.getValue(sim)
            }
            rucni.forEach { r -> ukupno += (r.vrijednostRucno(d.toEpochDay()) ?: 0.0) * faktoriRucnih.getValue(r.pozicija.id) }
            rez += d to ukupno
            d = d.plusDays(1)
        }
        return rez
    }
}
