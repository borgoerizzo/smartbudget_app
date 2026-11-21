package hr.smartbudget.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Tržišni podaci za sekciju "Ulaganja" (samo prikaz, bez trgovanja).
 * Izvor: javni podaci Yahoo Financea (gotove liste, cijene s odgodom i povijest cijena); ne treba ključ.
 */

enum class VrstaUlaganja(val naziv: String, val ikona: String, val opis: String, private val kratko: String? = null) {
    DIONICE("Dionice", "navTrend", "Pojedinačne tvrtke na burzama"),
    ETF("ETF-ovi", "navGrid", "Fondovi kojima se trguje kao dionicama"),
    // Nevidljivi razmak iza kose crte dopušta prijelom u dva retka na pločici.
    OBVEZNICE("Trezorski zapisi/​obveznice", "bank", "Prinosi državnih vrijednosnica i fondovi obveznica", kratko = "Obveznice"),
    ROBA("Roba", "star", "Zlato, srebro, nafta, plin i druge sirovine"),
    KRIPTO("Kripto", "wallet", "Kriptovalute"),
    ;

    /** Kratki naziv za pločicu u Ulaganjima. */
    val naslovPlocice: String get() = kratko ?: naziv
}

/** Konsenzus vanjskih analitičara: 1 = jaka kupnja … 5 = prodaja. */
data class OcjenaAnaliticara(val vrijednost: Double, val izvorno: String) {
    val oznaka: String get() = when {
        vrijednost <= 1.5 -> "Jaka kupnja"
        vrijednost <= 2.5 -> "Kupnja"
        vrijednost <= 3.5 -> "Drži"
        vrijednost <= 4.5 -> "Ispodprosječno"
        else -> "Prodaja"
    }

    companion object {
        /** "1.8 - Buy" → 1.8 */
        fun parsiraj(s: String?): OcjenaAnaliticara? {
            if (s.isNullOrBlank()) return null
            val v = s.substringBefore('-').trim().replace(',', '.').toDoubleOrNull() ?: return null
            return if (v in 1.0..5.0) OcjenaAnaliticara(v, s.substringAfter('-', "").trim()) else null
        }
    }
}

data class Kotacija(
    val simbol: String,
    val naziv: String,
    val valuta: String,
    val cijena: Double,
    /** Promjena od zadnjeg zatvaranja (dan za burze, 24 h za kripto). */
    val promjena: Double,
    val promjenaPosto: Double,
    val vrsta: VrstaUlaganja,
    /** Vrijednost je prinos u postocima (trezorski zapisi), a ne cijena. */
    val jePrinos: Boolean = false,
    val volumen: Long? = null,
    val prosjVolumen10: Long? = null,
    val prosjVolumen3m: Long? = null,
    val trzisnaKap: Double? = null,
    val pe: Double? = null,
    val dividendniPrinos: Double? = null,
    val min52: Double? = null,
    val max52: Double? = null,
    val dnevniMin: Double? = null,
    val dnevniMax: Double? = null,
    val otvaranje: Double? = null,
    val prethodnoZatvaranje: Double? = null,
    val burza: String? = null,
    val ocjena: OcjenaAnaliticara? = null,
)

/** Razdoblja grafa (kao u aplikacijama za ulaganje). */
enum class RazdobljeGrafa(val oznaka: String, val raspon: String, val interval: String, val sVremenom: Boolean) {
    DAN("1D", "1d", "5m", true),
    TJEDAN("1T", "5d", "30m", true),
    MJESEC("1M", "1mo", "60m", true),
    SEST_MJESECI("6M", "6mo", "1d", false),
    GODINA("1G", "1y", "1d", false),
    PET_GODINA("5G", "5y", "1wk", false),
    SVE("MAX", "max", "1mo", false),
}

data class TockaGrafa(val vrijeme: Long, val vrijednost: Double)

data class GrafCijene(
    val tocke: List<TockaGrafa>,
    /** Zatvaranje prije početka razdoblja (za 1D: jučerašnje zatvaranje). */
    val pocetak: Double?,
    val kotacija: Kotacija,
)

/** Jedna lista na zaslonu (npr. "Najtrgovanije"). */
data class ListaUlaganja(val naslov: String, val opis: String, val stavke: List<Kotacija>, val mjeriVolumen: Boolean = false)

object Trziste {
    private const val API = "https://query1.finance.yahoo.com"
    private const val AGENT = "Mozilla/5.0 (Linux; Android) SmartBudget"

    // --- Odabrani instrumenti (popularni na platformama za ulaganje) ---

    val popularneDionice = linkedMapOf(
        "AAPL" to "Apple", "NVDA" to "NVIDIA", "MSFT" to "Microsoft", "TSLA" to "Tesla",
        "AMZN" to "Amazon", "GOOGL" to "Alphabet (Google)", "META" to "Meta Platforms",
    )
    val indeksi = linkedMapOf("^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^GDAXI" to "DAX")
    val popularniEtf = linkedMapOf(
        "VWCE.DE" to "Vanguard FTSE All-World", "EUNL.DE" to "iShares Core MSCI World", "SXR8.DE" to "iShares Core S&P 500",
        "SPY" to "SPDR S&P 500", "QQQ" to "Invesco QQQ (Nasdaq 100)", "VOO" to "Vanguard S&P 500", "VTI" to "Vanguard Total Stock Market",
    )
    val prinosi = linkedMapOf(
        "^IRX" to "SAD trezorski zapis 13 tjedana", "^FVX" to "SAD obveznica 5 godina",
        "^TNX" to "SAD obveznica 10 godina", "^TYX" to "SAD obveznica 30 godina",
    )
    val etfObveznica = linkedMapOf(
        "TLT" to "iShares 20+ godina (SAD)", "IEF" to "iShares 7–10 godina (SAD)", "SHY" to "iShares 1–3 godine (SAD)",
        "BND" to "Vanguard Total Bond Market", "AGG" to "iShares Core US Aggregate Bond",
    )
    val roba = linkedMapOf(
        "GC=F" to "Zlato", "SI=F" to "Srebro", "PL=F" to "Platina", "HG=F" to "Bakar",
        "CL=F" to "Nafta (WTI)", "BZ=F" to "Nafta (Brent)", "NG=F" to "Prirodni plin",
        "ZW=F" to "Pšenica", "KC=F" to "Kava",
    )
    private val stabilni = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "USDE", "PYUSD", "USDS", "BUSD", "USD1")

    // --- Predmemorija: liste i cijene 60 s, povijest dulje ---

    private val predmemorija = ConcurrentHashMap<String, Pair<Long, String>>()

    private fun dohvati(url: String, trajanjeMs: Long = 60_000): String {
        predmemorija[url]?.let { (t, tekst) -> if (System.currentTimeMillis() - t < trajanjeMs) return tekst }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }.also { predmemorija[url] = System.currentTimeMillis() to it }
        } finally {
            c.disconnect()
        }
    }

    private fun kod(s: String) = URLEncoder.encode(s, "UTF-8")

    // --- Parsiranje (bez mreže, testira se) ---

    private fun JSONObject.broj(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private fun JSONObject.cijeli(k: String): Long? = broj(k)?.toLong()

    /** Rezultat gotove liste (screener) → kotacije. */
    fun parsirajListu(json: String, vrsta: VrstaUlaganja): List<Kotacija> {
        val rez = JSONObject(json).getJSONObject("finance").optJSONArray("result")?.optJSONObject(0) ?: return emptyList()
        val q = rez.optJSONArray("quotes") ?: return emptyList()
        return (0 until q.length()).mapNotNull { i ->
            val o = q.getJSONObject(i)
            val cijena = o.broj("regularMarketPrice") ?: return@mapNotNull null
            val simbol = o.optString("symbol").ifBlank { return@mapNotNull null }
            Kotacija(
                simbol = simbol,
                naziv = o.optString("longName").ifBlank { o.optString("shortName") }.ifBlank { simbol }
                    .let { if (vrsta == VrstaUlaganja.KRIPTO) it.removeSuffix(" USD") else it },
                valuta = o.optString("currency").ifBlank { "USD" },
                cijena = cijena,
                promjena = o.broj("regularMarketChange") ?: 0.0,
                promjenaPosto = o.broj("regularMarketChangePercent") ?: 0.0,
                vrsta = vrsta,
                volumen = o.cijeli("regularMarketVolume"),
                prosjVolumen10 = o.cijeli("averageDailyVolume10Day"),
                prosjVolumen3m = o.cijeli("averageDailyVolume3Month"),
                trzisnaKap = o.broj("marketCap"),
                pe = o.broj("trailingPE") ?: o.broj("forwardPE"),
                dividendniPrinos = o.broj("dividendYield") ?: o.broj("trailingAnnualDividendYield")?.times(100),
                min52 = o.broj("fiftyTwoWeekLow"),
                max52 = o.broj("fiftyTwoWeekHigh"),
                dnevniMin = o.broj("regularMarketDayLow"),
                dnevniMax = o.broj("regularMarketDayHigh"),
                otvaranje = o.broj("regularMarketOpen"),
                prethodnoZatvaranje = o.broj("regularMarketPreviousClose"),
                burza = o.optString("fullExchangeName").ifBlank { null },
                ocjena = OcjenaAnaliticara.parsiraj(o.optString("averageAnalystRating")),
            )
        }
    }

    /** Odgovor "spark" (više simbola odjednom) → kotacije i točke za mini-graf. */
    fun parsirajSpark(json: String, nazivi: Map<String, String>, vrsta: VrstaUlaganja, prinos: Boolean = false): List<Pair<Kotacija, List<Double>>> {
        val niz = JSONObject(json).getJSONObject("spark").optJSONArray("result") ?: return emptyList()
        val po = (0 until niz.length()).mapNotNull { i ->
            val r = niz.getJSONObject(i)
            val x = r.optJSONArray("response")?.optJSONObject(0) ?: return@mapNotNull null
            val m = x.getJSONObject("meta")
            val simbol = r.optString("symbol")
            val cijena = m.broj("regularMarketPrice") ?: return@mapNotNull null
            val prije = m.broj("chartPreviousClose") ?: m.broj("previousClose")
            val promjena = if (prije != null) cijena - prije else 0.0
            val zatvaranja = x.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
            simbol to (
                Kotacija(
                    simbol = simbol,
                    naziv = nazivi[simbol] ?: m.optString("longName").ifBlank { m.optString("shortName") }.ifBlank { simbol },
                    valuta = m.optString("currency").ifBlank { "USD" },
                    cijena = cijena,
                    promjena = promjena,
                    promjenaPosto = if (prije != null && prije != 0.0) promjena / prije * 100 else 0.0,
                    vrsta = vrsta,
                    jePrinos = prinos,
                    volumen = m.cijeli("regularMarketVolume")?.takeIf { it > 0 },
                    min52 = m.broj("fiftyTwoWeekLow"),
                    max52 = m.broj("fiftyTwoWeekHigh"),
                    dnevniMin = m.broj("regularMarketDayLow"),
                    dnevniMax = m.broj("regularMarketDayHigh"),
                    prethodnoZatvaranje = prije,
                    burza = m.optString("fullExchangeName").ifBlank { null },
                ) to brojevi(zatvaranja)
            )
        }.toMap()
        // Redoslijed kako je zadan (odabrane liste imaju smisleni poredak).
        val redoslijed = nazivi.keys.toList()
        return if (redoslijed.isEmpty()) po.values.toList() else redoslijed.mapNotNull { po[it] } + po.filterKeys { it !in nazivi }.values
    }

    /** Povijest cijena (chart) → točke grafa i podaci o instrumentu. */
    fun parsirajGraf(json: String, osnova: Kotacija?, vrsta: VrstaUlaganja, prinos: Boolean = false): GrafCijene {
        val r = JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val m = r.getJSONObject("meta")
        val vremena = r.optJSONArray("timestamp") ?: JSONArray()
        val zatvaranja = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close") ?: JSONArray()
        val tocke = (0 until minOf(vremena.length(), zatvaranja.length())).mapNotNull { i ->
            if (zatvaranja.isNull(i)) null else TockaGrafa(vremena.getLong(i) * 1000, zatvaranja.getDouble(i))
        }
        val simbol = m.optString("symbol")
        val cijena = m.broj("regularMarketPrice") ?: tocke.lastOrNull()?.vrijednost ?: 0.0
        // "chartPreviousClose" je zatvaranje prije početka razdoblja: jučerašnje samo za dnevni graf.
        val prethodno = m.broj("previousClose") ?: m.broj("chartPreviousClose")?.takeIf { m.optString("range") == "1d" }
        val promjena = if (prethodno != null) cijena - prethodno else 0.0
        val iz = Kotacija(
            simbol = simbol,
            naziv = osnova?.naziv ?: m.optString("longName").ifBlank { m.optString("shortName") }.ifBlank { simbol },
            valuta = m.optString("currency").ifBlank { osnova?.valuta ?: "USD" },
            cijena = cijena,
            promjena = promjena,
            promjenaPosto = if (prethodno != null && prethodno != 0.0) promjena / prethodno * 100 else 0.0,
            vrsta = vrsta,
            jePrinos = prinos,
            volumen = m.cijeli("regularMarketVolume")?.takeIf { it > 0 },
            min52 = m.broj("fiftyTwoWeekLow"),
            max52 = m.broj("fiftyTwoWeekHigh"),
            dnevniMin = m.broj("regularMarketDayLow"),
            dnevniMax = m.broj("regularMarketDayHigh"),
            prethodnoZatvaranje = prethodno,
            burza = m.optString("fullExchangeName").ifBlank { null },
        )
        // Podaci iz liste (P/E, kapitalizacija, ocjena…) imaju prednost gdje graf nema vrijednost.
        val k = if (osnova == null) iz else osnova.copy(
            cijena = iz.cijena, valuta = iz.valuta,
            promjena = if (prethodno != null) iz.promjena else osnova.promjena,
            promjenaPosto = if (prethodno != null) iz.promjenaPosto else osnova.promjenaPosto,
            volumen = iz.volumen ?: osnova.volumen, min52 = iz.min52 ?: osnova.min52, max52 = iz.max52 ?: osnova.max52,
            dnevniMin = iz.dnevniMin ?: osnova.dnevniMin, dnevniMax = iz.dnevniMax ?: osnova.dnevniMax,
            prethodnoZatvaranje = iz.prethodnoZatvaranje ?: osnova.prethodnoZatvaranje, burza = osnova.burza ?: iz.burza,
        )
        return GrafCijene(tocke, m.broj("chartPreviousClose"), k)
    }

    private fun brojevi(a: JSONArray?): List<Double> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { if (a.isNull(it)) null else a.getDouble(it) }

    // --- Mreža ---

    suspend fun lista(id: String, vrsta: VrstaUlaganja, koliko: Int = 25): List<Kotacija> = withContext(Dispatchers.IO) {
        parsirajListu(dohvati("$API/v1/finance/screener/predefined/saved?scrIds=$id&count=$koliko"), vrsta)
    }

    /** Cijene i mini-graf (današnji dan) za zadane simbole; više od 20 dijeli se u nekoliko upita. */
    suspend fun kotacije(nazivi: Map<String, String>, vrsta: VrstaUlaganja, prinos: Boolean = false): List<Pair<Kotacija, List<Double>>> =
        withContext(Dispatchers.IO) {
            nazivi.keys.chunked(20).flatMap { dio ->
                val json = dohvati("$API/v7/finance/spark?symbols=${kod(dio.joinToString(","))}&range=1d&interval=15m")
                parsirajSpark(json, nazivi.filterKeys { it in dio }, vrsta, prinos)
            }
        }

    /** Samo točke mini-grafa za simbole iz gotovih lista. */
    suspend fun miniGrafovi(simboli: List<String>): Map<String, List<Double>> = coroutineScope {
        simboli.distinct().chunked(20).map { dio ->
            async { runCatching { kotacije(dio.associateWith { "" }, VrstaUlaganja.DIONICE) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().associate { (k, t) -> k.simbol to t }
    }

    suspend fun graf(k: Kotacija, r: RazdobljeGrafa): GrafCijene = withContext(Dispatchers.IO) {
        val trajanje = if (r.sVremenom) 60_000L else 15 * 60_000L
        parsirajGraf(
            dohvati("$API/v8/finance/chart/${kod(k.simbol)}?range=${r.raspon}&interval=${r.interval}", trajanje),
            k, k.vrsta, k.jePrinos,
        )
    }

    /** Sve liste za jednu vrstu ulaganja (paralelno). */
    suspend fun liste(vrsta: VrstaUlaganja): Pair<List<ListaUlaganja>, Map<String, List<Double>>> = coroutineScope {
        when (vrsta) {
            VrstaUlaganja.DIONICE -> {
                val aktivne = async { lista("most_actives", vrsta, 50) }
                val rast = async { lista("day_gainers", vrsta, 10) }
                val pad = async { lista("day_losers", vrsta, 10) }
                val tehno = async { runCatching { lista("growth_technology_stocks", vrsta, 25) }.getOrDefault(emptyList()) }
                val podcijenjene = async { runCatching { lista("undervalued_large_caps", vrsta, 25) }.getOrDefault(emptyList()) }
                val popularne = async { kotacije(popularneDionice, vrsta) }
                val ind = async { runCatching { kotacije(indeksi, vrsta) }.getOrDefault(emptyList()) }
                val sveAktivne = aktivne.await()
                val preporuke = preporuke(sveAktivne + tehno.await() + podcijenjene.await())
                val pop = popularne.await()
                val liste = listOf(
                    ListaUlaganja("Indeksi", "Kretanje tržišta danas", ind.await().map { it.first }),
                    ListaUlaganja("Najtrgovanije", "Po broju dionica kojima se trgovalo", sveAktivne, mjeriVolumen = true),
                    ListaUlaganja("Preporuka analitičara", "Konsenzus vanjskih analitičara (1 = jaka kupnja, 5 = prodaja)", preporuke),
                    ListaUlaganja("Najveći rast danas", "Postotna promjena od jučerašnjeg zatvaranja", rast.await()),
                    ListaUlaganja("Najveći pad danas", "Postotna promjena od jučerašnjeg zatvaranja", pad.await()),
                    ListaUlaganja("Popularne", "Dionice koje ulagači najčešće prate", pop.map { it.first }),
                )
                val mini = (ind.await() + pop).associate { (k, t) -> k.simbol to t }
                liste to (mini + miniGrafovi(liste.drop(1).flatMap { l -> l.stavke.take(10).map { it.simbol } }.filter { it !in mini }))
            }
            VrstaUlaganja.ETF -> {
                val aktivni = async { lista("most_actives_etfs", vrsta, 25) }
                val popularni = async { kotacije(popularniEtf, vrsta) }
                val pop = popularni.await()
                val liste = listOf(
                    ListaUlaganja("Popularni", "Široko raspršeni fondovi, uključujući europske (UCITS)", pop.map { it.first }),
                    ListaUlaganja("Najtrgovaniji", "ETF-ovi s najvećim prometom (često s polugom)", aktivni.await(), mjeriVolumen = true),
                )
                val mini = pop.associate { (k, t) -> k.simbol to t }
                liste to (mini + miniGrafovi(liste[1].stavke.take(10).map { it.simbol }))
            }
            VrstaUlaganja.OBVEZNICE -> {
                val p = async { kotacije(prinosi, vrsta, prinos = true) }
                val e = async { kotacije(etfObveznica, vrsta) }
                val liste = listOf(
                    ListaUlaganja("Prinosi državnih vrijednosnica", "Godišnji prinos u postocima (SAD)", p.await().map { it.first }),
                    ListaUlaganja("Fondovi obveznica", "ETF-ovi koji ulažu u državne i korporativne obveznice", e.await().map { it.first }),
                )
                liste to (p.await() + e.await()).associate { (k, t) -> k.simbol to t }
            }
            VrstaUlaganja.ROBA -> {
                val sve = kotacije(roba, vrsta)
                val k = sve.map { it.first }
                listOf(
                    ListaUlaganja("Popularna roba", "Terminski ugovori (futures), cijene u USD", k),
                    ListaUlaganja("Najveći pomaci danas", "Najveća promjena u odnosu na jučer", k.sortedByDescending { abs(it.promjenaPosto) }),
                ) to sve.associate { (x, t) -> x.simbol to t }
            }
            VrstaUlaganja.KRIPTO -> {
                val sve = lista("all_cryptocurrencies_us", vrsta, 50)
                val bezStabilnih = sve.filter { it.simbol.substringBefore('-') !in stabilni }
                val liste = listOf(
                    ListaUlaganja("Najtrgovanije", "Promet u zadnja 24 sata", bezStabilnih.sortedByDescending { it.volumen ?: 0 }, mjeriVolumen = true),
                    ListaUlaganja("Najveći rast (24 h)", "Postotna promjena u zadnja 24 sata", bezStabilnih.sortedByDescending { it.promjenaPosto }),
                    ListaUlaganja("Najveći pad (24 h)", "Postotna promjena u zadnja 24 sata", bezStabilnih.sortedBy { it.promjenaPosto }),
                    ListaUlaganja("Po tržišnoj kapitalizaciji", "Najveće kriptovalute", sve.sortedByDescending { it.trzisnaKap ?: 0.0 }),
                )
                liste to miniGrafovi(liste.flatMap { l -> l.stavke.take(10).map { it.simbol } })
            }
        }
    }

    // --- Ulaganja korisnika: pretraga, cijene s ocjenom analitičara, dnevna povijest ---

    /** Rezultat pretrage instrumenta (po nazivu, oznaci ili ISIN-u). */
    data class Pronadeno(val simbol: String, val naziv: String, val vrsta: VrstaUlaganja, val burza: String?)

    /** Vrsta s Yahoo Financea; kriptovalute i ostalo se ne nude (kripto je u novčanicima). */
    fun vrstaIzTipa(tip: String): VrstaUlaganja? = when (tip.uppercase()) {
        "EQUITY" -> VrstaUlaganja.DIONICE
        "ETF", "MUTUALFUND" -> VrstaUlaganja.ETF
        "FUTURE" -> VrstaUlaganja.ROBA
        "INDEX", "BOND" -> VrstaUlaganja.OBVEZNICE
        else -> null
    }

    fun parsirajPretragu(json: String): List<Pronadeno> {
        val q = JSONObject(json).optJSONArray("quotes") ?: return emptyList()
        return (0 until q.length()).mapNotNull { i ->
            val o = q.getJSONObject(i)
            val simbol = o.optString("symbol").ifBlank { return@mapNotNull null }
            val vrsta = vrstaIzTipa(o.optString("quoteType")) ?: return@mapNotNull null
            Pronadeno(
                simbol,
                o.optString("longname").ifBlank { o.optString("shortname") }.ifBlank { simbol },
                vrsta,
                o.optString("exchDisp").ifBlank { o.optString("exchange") }.ifBlank { null },
            )
        }
    }

    suspend fun pretrazi(upit: String): List<Pronadeno> = withContext(Dispatchers.IO) {
        if (upit.isBlank()) return@withContext emptyList()
        parsirajPretragu(dohvati("$API/v1/finance/search?q=${kod(upit.trim())}&quotesCount=10&newsCount=0&listsCount=0", 10 * 60_000))
    }

    // "Crumb" (oznaka sesije s kolačićem) treba za upit cijena s ocjenama analitičara; dohvaća se jednom.
    @Volatile private var sesija: Pair<String, String>? = null

    private fun novaSesija(): Pair<String, String> {
        val c = URL("https://fc.yahoo.com").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", AGENT)
        val kolacic = try {
            c.responseCode // odgovor je obično 404, bitan je samo kolačić
            c.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value }
                .joinToString("; ") { it.substringBefore(';') }
        } finally {
            c.disconnect()
        }
        val g = URL("$API/v1/test/getcrumb").openConnection() as HttpURLConnection
        g.connectTimeout = 8_000
        g.readTimeout = 8_000
        g.setRequestProperty("User-Agent", AGENT)
        g.setRequestProperty("Cookie", kolacic)
        try {
            if (g.responseCode !in 200..299) error("crumb HTTP ${g.responseCode}")
            val crumb = g.inputStream.bufferedReader().use { it.readText() }.trim()
            if (crumb.isEmpty() || crumb.contains('<')) error("crumb")
            return (kolacic to crumb).also { sesija = it }
        } finally {
            g.disconnect()
        }
    }

    /** Upit cijena (v7 quote) za zadane simbole: cijena, promjena, valuta i ocjena analitičara. */
    private fun quote(simboli: List<String>): List<Kotacija> {
        fun pokusaj(s: Pair<String, String>): Pair<Int, String> {
            val c = URL("$API/v7/finance/quote?symbols=${kod(simboli.joinToString(","))}&crumb=${kod(s.second)}").openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 12_000
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Cookie", s.first)
            try {
                return c.responseCode to if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else ""
            } finally {
                c.disconnect()
            }
        }
        var (status, tekst) = pokusaj(sesija ?: novaSesija())
        if (status == 401 || status == 403) {
            val r = pokusaj(novaSesija()); status = r.first; tekst = r.second
        }
        if (status !in 200..299) error("HTTP $status")
        val niz = JSONObject(tekst).getJSONObject("quoteResponse").optJSONArray("result") ?: return emptyList()
        // Isti oblik kao gotova lista: koristi se isto parsiranje.
        return parsirajListu(JSONObject().put("finance", JSONObject().put("result", JSONArray().put(JSONObject().put("quotes", niz)))).toString(), VrstaUlaganja.DIONICE)
    }

    /**
     * Trenutne cijene ulaganja korisnika s ocjenom analitičara gdje postoji. Ako upit s ocjenama ne uspije,
     * cijene se dohvaćaju bez ocjena (isti izvor kao mini-grafovi).
     */
    suspend fun kotacijeUlaganja(simboli: List<String>): Map<String, Kotacija> = withContext(Dispatchers.IO) {
        if (simboli.isEmpty()) return@withContext emptyMap()
        val rez = runCatching { simboli.distinct().chunked(40).flatMap { quote(it) } }
            .getOrElse { kotacije(simboli.associateWith { "" }, VrstaUlaganja.DIONICE).map { it.first } }
        rez.associateBy { it.simbol }
    }

    fun parsirajDnevnuPovijest(json: String): List<Pair<java.time.LocalDate, Double>> {
        val r = JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val zona = runCatching { java.time.ZoneId.of(r.getJSONObject("meta").optString("exchangeTimezoneName")) }.getOrDefault(java.time.ZoneOffset.UTC)
        val vremena = r.optJSONArray("timestamp") ?: return emptyList()
        val z = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close") ?: return emptyList()
        return (0 until minOf(vremena.length(), z.length())).mapNotNull { i ->
            if (z.isNull(i)) null else java.time.Instant.ofEpochSecond(vremena.getLong(i)).atZone(zona).toLocalDate() to z.getDouble(i)
        }.groupBy { it.first }.map { (d, l) -> d to l.last().second }.sortedBy { it.first }
    }

    /** Dnevne cijene zatvaranja za zadnjih [dana] dana (u valuti kotacije). */
    suspend fun dnevnaPovijest(simbol: String, dana: Int): List<Pair<java.time.LocalDate, Double>> = withContext(Dispatchers.IO) {
        val raspon = when {
            dana <= 31 -> "1mo"
            dana <= 93 -> "3mo"
            dana <= 186 -> "6mo"
            dana <= 366 -> "1y"
            else -> "5y"
        }
        parsirajDnevnuPovijest(dohvati("$API/v8/finance/chart/${kod(simbol)}?range=$raspon&interval=1d", 30 * 60_000))
    }

    /** Dionice s najboljim konsenzusom analitičara (do 2,0 = kupnja ili jaka kupnja), bez ponavljanja. */
    fun preporuke(kandidati: List<Kotacija>): List<Kotacija> =
        kandidati.filter { (it.ocjena?.vrijednost ?: 9.0) <= 2.0 }
            .distinctBy { it.simbol }
            .sortedWith(compareBy<Kotacija> { it.ocjena!!.vrijednost }.thenByDescending { it.volumen ?: 0 })

    /** Poredak liste "Najtrgovanije" po prometu: danas, prosjek 10 dana ili 3 mjeseca. */
    fun poPrometu(stavke: List<Kotacija>, dana: Int): List<Kotacija> = stavke.sortedByDescending {
        when (dana) {
            1 -> it.volumen
            10 -> it.prosjVolumen10 ?: it.volumen
            else -> it.prosjVolumen3m ?: it.volumen
        } ?: 0L
    }
}

/** Oblikovanje tržišnih brojeva na hrvatski način. */
object FormatTrzista {
    private val simboli = mapOf("USD" to "$", "EUR" to "€", "GBP" to "£", "GBp" to "p", "JPY" to "¥", "CHF" to "CHF")

    fun broj(v: Double, decimala: Int): String {
        val s = String.format(java.util.Locale.ROOT, "%,.${decimala}f", v)
        return s.replace(',', 'X').replace('.', ',').replace('X', '.')
    }

    /** Više decimala za male cijene (npr. kriptovalute ispod 1). */
    fun decimale(v: Double): Int = when {
        abs(v) >= 1.0 -> 2
        abs(v) >= 0.01 -> 4
        else -> 6
    }

    fun cijena(v: Double, valuta: String, prinos: Boolean = false): String =
        if (prinos) broj(v, 3) + " %" else broj(v, decimale(v)) + " " + (simboli[valuta] ?: valuta)

    fun promjenaPosto(p: Double): String = (if (p > 0) "+" else if (p < 0) "−" else "") + broj(abs(p), 2) + " %"

    fun promjena(v: Double, valuta: String, prinos: Boolean = false): String =
        (if (v > 0) "+" else if (v < 0) "−" else "") + if (prinos) broj(abs(v), 3) + " p.b." else cijena(abs(v), valuta)

    /** 1.234.567 → "1,23 mil."; tržišna kapitalizacija i promet. */
    fun veliki(v: Double): String = when {
        abs(v) >= 1e12 -> broj(v / 1e12, 2) + " bil."
        abs(v) >= 1e9 -> broj(v / 1e9, 2) + " mlrd."
        abs(v) >= 1e6 -> broj(v / 1e6, 2) + " mil."
        abs(v) >= 1e3 -> broj(v / 1e3, 1) + " tis."
        else -> broj(v, 0)
    }
}
