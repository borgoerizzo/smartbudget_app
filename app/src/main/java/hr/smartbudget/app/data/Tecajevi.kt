package hr.smartbudget.app.data

import android.content.Context
import hr.smartbudget.app.domain.Valuta
import hr.smartbudget.app.domain.Valute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Srednji tečaj HNB-a: koliko jedinica valute vrijedi 1 euro (EUR = 1). */
data class TecajnaLista(
    val datum: LocalDate,
    val broj: String,
    val tecajevi: Map<String, Double>,
    val dohvaceno: Long,
    /** Zadnja spremljena lista jer dohvat nije uspio (nema interneta). */
    val izSpremljenog: Boolean = false,
    /** "HNB" ili "ECB" (rezerva kad HNB nije dostupan; srednji tečaj HNB-a jednak je referentnom tečaju ECB-a). */
    val izvor: String = "HNB",
)

/** Cijene kriptovaluta u eurima i promjena u zadnja 24 sata (%), s CoinGecka. */
data class KriptoCijene(
    val cijeneEur: Map<String, Double>,
    val promjena24h: Map<String, Double>,
    val dohvaceno: Long,
    val izSpremljenog: Boolean = false,
)

/** Dohvat tečajeva: HNB (srednji tečaj) za valute, CoinGecko za kriptovalute. Zadnji uspješan dohvat se pamti za rad bez interneta. */
object Tecajevi {
    private const val HNB = "https://api.hnb.hr/tecajn-eur/v3"
    private const val ECB = "https://api.frankfurter.dev/v1/latest?base=EUR"
    private const val COINGECKO = "https://api.coingecko.com/api/v3"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tecajevi", Context.MODE_PRIVATE)

    private fun dohvati(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "SmartBudget-Android")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // --- HNB ---

    fun parsirajHnb(json: String, dohvaceno: Long): TecajnaLista {
        val niz = JSONArray(json)
        val mapa = mutableMapOf("EUR" to 1.0)
        var datum = LocalDate.now()
        var broj = ""
        for (i in 0 until niz.length()) {
            val o = niz.getJSONObject(i)
            mapa[o.getString("valuta")] = o.getString("srednji_tecaj").replace(',', '.').toDouble()
            datum = LocalDate.parse(o.getString("datum_primjene"))
            broj = o.optString("broj_tecajnice")
        }
        return TecajnaLista(datum, broj, mapa, dohvaceno)
    }

    /** Referentni tečaj ECB-a (Frankfurter); BAM je vezana uz euro fiksnim tečajem. */
    fun parsirajEcb(json: String, dohvaceno: Long): TecajnaLista {
        val o = JSONObject(json)
        val r = o.getJSONObject("rates")
        val mapa = mutableMapOf("EUR" to 1.0, "BAM" to 1.95583)
        r.keys().forEach { mapa[it] = r.getDouble(it) }
        return TecajnaLista(LocalDate.parse(o.getString("date")), "", mapa, dohvaceno, izvor = "ECB")
    }

    /**
     * Srednji tečaj HNB-a. Ako HNB nije dostupan (neke mreže ili VPN blokiraju hnb.hr), isti tečaj
     * dohvaća se kao referentni tečaj ECB-a; bez interneta vraća se zadnja spremljena lista.
     */
    suspend fun hnb(ctx: Context): Result<TecajnaLista> = withContext(Dispatchers.IO) {
        runCatching {
            val json = dohvati(HNB)
            val sada = System.currentTimeMillis()
            parsirajHnb(json, sada).also { prefs(ctx).edit().putString("hnb", json).putString("hnbIzvor", "HNB").putLong("hnbVrijeme", sada).apply() }
        }.recoverCatching {
            val json = dohvati(ECB)
            val sada = System.currentTimeMillis()
            parsirajEcb(json, sada).also { prefs(ctx).edit().putString("hnb", json).putString("hnbIzvor", "ECB").putLong("hnbVrijeme", sada).apply() }
        }.recoverCatching { greska ->
            val json = prefs(ctx).getString("hnb", null) ?: throw greska
            val vrijeme = prefs(ctx).getLong("hnbVrijeme", 0)
            (if (prefs(ctx).getString("hnbIzvor", "HNB") == "ECB") parsirajEcb(json, vrijeme) else parsirajHnb(json, vrijeme)).copy(izSpremljenog = true)
        }
    }

    /** Zadnje spremljene liste (bez mreže), za prikaz odmah dok se dohvaćaju svježe. */
    fun spremljeno(ctx: Context): Pair<TecajnaLista?, KriptoCijene?> {
        val p = prefs(ctx)
        val h = p.getString("hnb", null)?.let { json ->
            runCatching {
                val t = p.getLong("hnbVrijeme", 0)
                (if (p.getString("hnbIzvor", "HNB") == "ECB") parsirajEcb(json, t) else parsirajHnb(json, t)).copy(izSpremljenog = true)
            }.getOrNull()
        }
        val k = p.getString("kripto", null)?.let { json -> runCatching { parsirajKripto(json, p.getLong("kriptoVrijeme", 0)).copy(izSpremljenog = true) }.getOrNull() }
        return h to k
    }

    // --- CoinGecko ---

    fun parsirajKripto(json: String, dohvaceno: Long): KriptoCijene {
        val o = JSONObject(json)
        val cijene = mutableMapOf<String, Double>()
        val promjene = mutableMapOf<String, Double>()
        Valute.kripto.forEach { v ->
            o.optJSONObject(v.coingeckoId!!)?.let {
                cijene[v.kod] = it.getDouble("eur")
                if (it.has("eur_24h_change") && !it.isNull("eur_24h_change")) promjene[v.kod] = it.getDouble("eur_24h_change")
            }
        }
        return KriptoCijene(cijene, promjene, dohvaceno)
    }

    suspend fun kripto(ctx: Context): Result<KriptoCijene> = withContext(Dispatchers.IO) {
        runCatching {
            val ids = Valute.kripto.joinToString(",") { it.coingeckoId!! }
            val json = dohvati("$COINGECKO/simple/price?ids=$ids&vs_currencies=eur&include_24hr_change=true")
            val sada = System.currentTimeMillis()
            parsirajKripto(json, sada).also { prefs(ctx).edit().putString("kripto", json).putLong("kriptoVrijeme", sada).apply() }
        }.recoverCatching { greska ->
            val json = prefs(ctx).getString("kripto", null) ?: throw greska
            parsirajKripto(json, prefs(ctx).getLong("kriptoVrijeme", 0)).copy(izSpremljenog = true)
        }
    }

    /** Dnevne cijene kriptovalute u eurima za zadnjih [dana] dana (najstarija prva). */
    private fun parsirajPovijest(json: String): List<Pair<LocalDate, Double>> {
        val niz = JSONObject(json).getJSONArray("prices")
        return (0 until niz.length()).map {
            val t = niz.getJSONArray(it)
            Instant.ofEpochMilli(t.getLong(0)).atZone(ZoneOffset.UTC).toLocalDate() to t.getDouble(1)
        }.groupBy { it.first }.map { (d, l) -> d to l.last().second }.sortedBy { it.first }
    }

    /**
     * Dnevne cijene kriptovalute za zadnjih [dana] dana. Povijest se mijenja jednom dnevno, pa se dohvaćena
     * čuva 30 minuta (CoinGecko besplatno ograničava broj upita); ako dohvat ne uspije, koristi se zadnja spremljena.
     */
    suspend fun povijest(ctx: Context, v: Valuta, dana: Int): Result<List<Pair<LocalDate, Double>>> = withContext(Dispatchers.IO) {
        val kljuc = "povijest_${v.kod}_$dana"
        val p = prefs(ctx)
        val spremljeno = p.getString(kljuc, null)
        if (spremljeno != null && System.currentTimeMillis() - p.getLong(kljuc + "_t", 0) < 30 * 60_000) {
            return@withContext runCatching { parsirajPovijest(spremljeno) }
        }
        runCatching {
            var json = runCatching { dohvati("$COINGECKO/coins/${v.coingeckoId}/market_chart?vs_currency=eur&days=$dana&interval=daily") }
            if (json.isFailure) { kotlinx.coroutines.delay(1_500); json = runCatching { dohvati("$COINGECKO/coins/${v.coingeckoId}/market_chart?vs_currency=eur&days=$dana&interval=daily") } }
            val tekst = json.getOrThrow()
            parsirajPovijest(tekst).also { p.edit().putString(kljuc, tekst).putLong(kljuc + "_t", System.currentTimeMillis()).apply() }
        }.recoverCatching { greska -> parsirajPovijest(spremljeno ?: throw greska) }
    }

    /**
     * Preračun između bilo koje dvije valute preko eura: fiat po srednjem tečaju HNB-a, kriptovalute po cijeni u eurima.
     * null kad za neku od valuta nema tečaja.
     */
    fun pretvori(iznos: Double, iz: Valuta, u: Valuta, hnb: TecajnaLista?, kripto: KriptoCijene?): Double? {
        if (iz.kod == u.kod) return iznos
        val uEur = when {
            iz.kripto -> kripto?.cijeneEur?.get(iz.kod)?.let { iznos * it }
            iz.kod == "EUR" -> iznos
            else -> hnb?.tecajevi?.get(iz.kod)?.let { iznos / it }
        } ?: return null
        return when {
            u.kripto -> kripto?.cijeneEur?.get(u.kod)?.let { uEur / it }
            u.kod == "EUR" -> uEur
            else -> hnb?.tecajevi?.get(u.kod)?.let { uEur * it }
        }
    }
}
