package hr.smartbudget.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/** Postavke sigurnosti, privatnosti i obavijesti (izvan baze; u sigurnosnu kopiju ulaze sve osim PIN-a i zaključavanja). */
data class StanjePostavki(
    val zakljucavanje: Boolean = false,
    val biometrija: Boolean = true,
    /** Zaključaj nakon toliko sekundi u pozadini (0 = odmah). */
    val odgodaSekundi: Int = 60,
    val imaPin: Boolean = false,
    val sakrijStanje: Boolean = false,
    val sakrijPrihod: Boolean = false,
    val obavijestPonavljajuci: Boolean = false,
    val obavijestMinus: Boolean = false,
    val obavijestPrognoza: Boolean = false,
    val podsjetnik: Boolean = false,
    /** Vrijeme dnevnog podsjetnika u minutama od ponoći. */
    val podsjetnikMinuta: Int = 20 * 60,
    val tjedni: Boolean = false,
    /** Dan tjednog pregleda: 1 = ponedjeljak … 7 = nedjelja. */
    val tjedniDan: Int = 7,
    val tjedniMinuta: Int = 19 * 60,
    /** Račun koji je unaprijed odabran pri novom unosu; null = Kartica. */
    val zadaniRacun: String? = null,
    /** Osnovna valuta aplikacije (kod, npr. EUR, CHF). */
    val valuta: String = "EUR",
    /** Što zaključavanje štiti: SVE (cijela aplikacija), CIKLUS ili STEDNJA. */
    val opsegZakljucavanja: String = "SVE",
    /** Iznosi štednje skriveni (neovisno o skrivanju stanja računa). */
    val sakrijStednju: Boolean = false,
    /** Raspored kartica po zaslonu ("analitika", "budzeti"): redoslijed ključeva i skrivene kartice. */
    val rasporedi: Map<String, List<String>> = emptyMap(),
    val skrivene: Map<String, Set<String>> = emptyMap(),
)

class AppPostavke private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("postavke_aplikacije", Context.MODE_PRIVATE)

    private val _stanje = MutableStateFlow(ucitaj())
    val stanje: StateFlow<StanjePostavki> = _stanje

    private fun ucitaj() = StanjePostavki(
        zakljucavanje = prefs.getBoolean("zakljucavanje", false),
        biometrija = prefs.getBoolean("biometrija", true),
        odgodaSekundi = prefs.getInt("odgoda", 60),
        imaPin = prefs.getString("pinHash", null) != null,
        sakrijStanje = prefs.getBoolean("sakrijStanje", false),
        sakrijPrihod = prefs.getBoolean("sakrijPrihod", false),
        obavijestPonavljajuci = prefs.getBoolean("obavijestPonavljajuci", false),
        obavijestMinus = prefs.getBoolean("obavijestMinus", false),
        obavijestPrognoza = prefs.getBoolean("obavijestPrognoza", false),
        podsjetnik = prefs.getBoolean("podsjetnik", false),
        podsjetnikMinuta = prefs.getInt("podsjetnikMinuta", 20 * 60),
        tjedni = prefs.getBoolean("tjedni", false),
        tjedniDan = prefs.getInt("tjedniDan", 7),
        tjedniMinuta = prefs.getInt("tjedniMinuta", 19 * 60),
        zadaniRacun = prefs.getString("zadaniRacun", null),
        valuta = prefs.getString("valuta", "EUR") ?: "EUR",
        opsegZakljucavanja = prefs.getString("opsegZakljucavanja", "SVE") ?: "SVE",
        sakrijStednju = prefs.getBoolean("sakrijStednju", false),
        rasporedi = ZASLONI.associateWith { z -> prefs.getString("raspored_$z", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList() },
        skrivene = ZASLONI.associateWith { z -> prefs.getStringSet("skrivene_$z", emptySet())!!.toSet() },
    ).also { primijeniValutu(it.valuta) }

    private fun primijeniValutu(kod: String) {
        val v = hr.smartbudget.app.domain.Valute.po(kod)
        hr.smartbudget.app.domain.Novac.simbol = v.simbol
        hr.smartbudget.app.domain.Novac.kodValute = v.kod
    }

    private fun postavi(kljuc: String, v: Boolean) { prefs.edit().putBoolean(kljuc, v).apply(); _stanje.value = ucitaj() }

    fun postaviZakljucavanje(v: Boolean) = postavi("zakljucavanje", v)
    fun postaviBiometriju(v: Boolean) = postavi("biometrija", v)
    fun postaviSakrijStanje(v: Boolean) = postavi("sakrijStanje", v)
    fun postaviSakrijPrihod(v: Boolean) = postavi("sakrijPrihod", v)
    fun postaviSakrijStednju(v: Boolean) = postavi("sakrijStednju", v)
    fun postaviOpsegZakljucavanja(o: String) { prefs.edit().putString("opsegZakljucavanja", o).apply(); _stanje.value = ucitaj() }
    fun postaviObavijestPonavljajuci(v: Boolean) = postavi("obavijestPonavljajuci", v)
    fun postaviObavijestMinus(v: Boolean) = postavi("obavijestMinus", v)
    fun postaviObavijestPrognoza(v: Boolean) = postavi("obavijestPrognoza", v)
    fun postaviPodsjetnik(v: Boolean) = postavi("podsjetnik", v)
    fun postaviTjedni(v: Boolean) = postavi("tjedni", v)
    fun postaviTjedniDan(d: Int) { prefs.edit().putInt("tjedniDan", d).apply(); _stanje.value = ucitaj() }
    fun postaviTjedniMinuta(m: Int) { prefs.edit().putInt("tjedniMinuta", m).apply(); _stanje.value = ucitaj() }
    fun postaviRaspored(zaslon: String, redoslijed: List<String>, skrivene: Set<String>) {
        prefs.edit().putString("raspored_$zaslon", redoslijed.joinToString(",")).putStringSet("skrivene_$zaslon", skrivene).apply()
        _stanje.value = ucitaj()
    }

    fun vratiZadaniRaspored(zaslon: String) {
        prefs.edit().remove("raspored_$zaslon").remove("skrivene_$zaslon").apply()
        _stanje.value = ucitaj()
    }

    fun postaviValutu(kod: String) { prefs.edit().putString("valuta", kod).apply(); _stanje.value = ucitaj() }
    fun postaviZadaniRacun(naziv: String) { prefs.edit().putString("zadaniRacun", naziv).apply(); _stanje.value = ucitaj() }
    fun postaviPodsjetnikMinuta(m: Int) { prefs.edit().putInt("podsjetnikMinuta", m).apply(); _stanje.value = ucitaj() }

    /** Prognoza se javlja jednom po kategoriji u ciklusu. */
    fun prognozaPoslana(kljuc: String) = prefs.getStringSet("prognozePoslane", emptySet())!!.contains(kljuc)
    fun oznaciPrognozu(kljuc: String) {
        val skup = prefs.getStringSet("prognozePoslane", emptySet())!!.toMutableSet().apply { add(kljuc) }
        prefs.edit().putStringSet("prognozePoslane", skup).apply()
    }
    fun postaviOdgodu(sekundi: Int) { prefs.edit().putInt("odgoda", sekundi).apply(); _stanje.value = ucitaj() }

    /** PIN se sprema samo kao SHA-256 sažetak uz nasumičnu sol. */
    fun postaviPin(pin: String) {
        val sol = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pinSol", sol).putString("pinHash", sazetak(sol, pin)).apply()
        _stanje.value = ucitaj()
    }

    /** Oporavak: briše PIN i isključuje zaključavanje (nakon potvrde zaključavanjem zaslona mobitela). */
    fun ukloniPin() {
        prefs.edit().remove("pinHash").remove("pinSol").putBoolean("zakljucavanje", false).apply()
        _stanje.value = ucitaj()
    }

    fun provjeriPin(pin: String): Boolean {
        val sol = prefs.getString("pinSol", null) ?: return false
        return prefs.getString("pinHash", null) == sazetak(sol, pin)
    }

    private fun sazetak(sol: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((sol + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Postavke za sigurnosnu kopiju. PIN i zaključavanje se ne kopiraju (datoteka kopije nije šifrirana,
     * a kratki PIN bi se iz sažetka lako pogodio); nakon vraćanja ih treba ponovno postaviti.
     */
    fun zaKopiju(): org.json.JSONObject = org.json.JSONObject().apply {
        prefs.all.forEach { (k, v) ->
            if (k in NE_KOPIRA) return@forEach
            val o = when (v) {
                is Boolean -> org.json.JSONObject().put("t", "b").put("v", v)
                is Int -> org.json.JSONObject().put("t", "i").put("v", v)
                is Long -> org.json.JSONObject().put("t", "l").put("v", v)
                is String -> org.json.JSONObject().put("t", "s").put("v", v)
                is Set<*> -> org.json.JSONObject().put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            put(k, o)
        }
    }

    /** Vraća postavke iz kopije; postojeći PIN i zaključavanje ostaju kakvi jesu. */
    fun vratiIzKopije(o: org.json.JSONObject) {
        val e = prefs.edit()
        o.keys().forEach { k ->
            if (k in NE_KOPIRA) return@forEach
            val p = o.optJSONObject(k) ?: return@forEach
            when (p.optString("t")) {
                "b" -> e.putBoolean(k, p.getBoolean("v"))
                "i" -> e.putInt(k, p.getInt("v"))
                "l" -> e.putLong(k, p.getLong("v"))
                "s" -> e.putString(k, p.getString("v"))
                "ss" -> p.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        e.apply()
        _stanje.value = ucitaj()
    }

    /** Za obavijest o minusu: šalje se samo pri prelasku iz plusa u minus, ne kod svake promjene. */
    var zadnjeStanjeNegativno: Boolean
        get() = prefs.getBoolean("zadnjeNegativno", false)
        set(v) { prefs.edit().putBoolean("zadnjeNegativno", v).apply() }

    companion object {
        @Volatile private var instance: AppPostavke? = null
        fun get(context: Context): AppPostavke = instance ?: synchronized(this) {
            instance ?: AppPostavke(context.applicationContext).also { instance = it }
        }

        /** Ključevi koji ne ulaze u sigurnosnu kopiju (sigurnost i interno stanje obavijesti). */
        private val NE_KOPIRA = setOf("pinHash", "pinSol", "zakljucavanje", "prognozePoslane", "zadnjeNegativno")

        /** Prikaz skrivenog iznosa. */
        val ZASLONI = listOf("analitika", "budzeti")
        val SKRIVENO: String get() = "•••• " + hr.smartbudget.app.domain.Novac.simbol
    }
}
