package hr.smartbudget.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import hr.smartbudget.app.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class UcestalostBackupa(val naziv: String, val kratko: String) {
    SVAKI_UNOS("Nakon svakog unosa", "Svaki unos"),
    TJEDNO("Jednom tjedno", "Tjedno"),
}

data class StanjeBackupa(
    val ukljucen: Boolean = false,
    val ucestalost: UcestalostBackupa = UcestalostBackupa.SVAKI_UNOS,
    /** Mapa (SAF stablo) u koju se spremaju kopije; null dok je korisnik ne odabere. */
    val mapa: Uri? = null,
    val nazivMape: String? = null,
    val zadnji: Long? = null,
    val zadnjaDatoteka: String? = null,
    val greska: String? = null,
    val radi: Boolean = false,
)

data class SadrzajKopije(
    val vrijeme: String,
    val verzijaAplikacije: String,
    val kategorije: List<Kategorija>,
    val transakcije: List<Transakcija>,
    val ponavljajuci: List<PonavljajuciUnos>,
    val postavke: PostavkeCiklusa,
    val prilagodbe: List<PrilagodbaCiklusa> = emptyList(),
    val racuni: List<Racun> = emptyList(),
    val ciljevi: List<CiljStednje> = emptyList(),
    val uplate: List<UplataCilja> = emptyList(),
    val novcanici: List<Novcanik> = emptyList(),
    val promjeneNovcanika: List<PromjenaNovcanika> = emptyList(),
    /** Postavke aplikacije (valuta, privatnost, obavijesti, raspored kartica…), bez PIN-a; null u starijim kopijama. */
    val postavkeAplikacije: JSONObject? = null,
    val pozicije: List<Pozicija> = emptyList(),
    val promjenePozicija: List<PromjenaPozicije> = emptyList(),
)

/**
 * Sigurnosne kopije svih podataka u JSON datoteku u mapi koju korisnik odabere (preživi i deinstalaciju).
 * Automatski: nakon svakog unosa (uz kratku odgodu) ili jednom tjedno; uvijek i na zahtjev.
 */
class BackupManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val repo get() = BudgetRepository.get(context)
    private val zakljucaj = Mutex()
    private var odgoda: Job? = null

    private val _stanje = MutableStateFlow(ucitaj())
    val stanje: StateFlow<StanjeBackupa> = _stanje

    private fun ucitaj() = StanjeBackupa(
        ukljucen = prefs.getBoolean("ukljucen", false),
        ucestalost = runCatching { UcestalostBackupa.valueOf(prefs.getString("ucestalost", null)!!) }.getOrDefault(UcestalostBackupa.SVAKI_UNOS),
        mapa = prefs.getString("mapa", null)?.let(Uri::parse),
        nazivMape = prefs.getString("nazivMape", null),
        zadnji = prefs.getLong("zadnji", 0L).takeIf { it > 0 },
        zadnjaDatoteka = prefs.getString("zadnjaDatoteka", null),
        greska = prefs.getString("greska", null),
    )

    private fun osvjeziStanje() { _stanje.value = ucitaj().copy(radi = _stanje.value.radi) }

    fun postaviUkljucen(da: Boolean) { prefs.edit().putBoolean("ukljucen", da).apply(); osvjeziStanje() }

    fun postaviUcestalost(u: UcestalostBackupa) { prefs.edit().putString("ucestalost", u.name).apply(); osvjeziStanje() }

    /** Sprema odabranu mapu i trajno zadržava pravo pisanja u nju. */
    fun postaviMapu(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val naziv = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        prefs.edit().putString("mapa", uri.toString()).putString("nazivMape", naziv).remove("greska").apply()
        osvjeziStanje()
    }

    /** Poziva se nakon svake promjene podataka; kopija se radi 3 s nakon zadnje promjene u nizu. */
    fun nakonPromjene() {
        val s = _stanje.value
        if (!s.ukljucen || s.ucestalost != UcestalostBackupa.SVAKI_UNOS || s.mapa == null) return
        odgoda?.cancel()
        odgoda = repo.opseg.launch {
            delay(3_000)
            napraviKopiju(automatska = true)
        }
    }

    /** Tjedna kopija: ako je od zadnje prošlo 7 ili više dana (poziva se iz pozadinskog posla). */
    suspend fun tjednaAkoTreba() {
        val s = _stanje.value
        if (!s.ukljucen || s.ucestalost != UcestalostBackupa.TJEDNO || s.mapa == null) return
        val zadnji = s.zadnji ?: 0L
        if (System.currentTimeMillis() - zadnji >= 7L * 24 * 3600 * 1000) napraviKopiju(automatska = true)
    }

    /** Radi kopiju u odabranu mapu; vraća poruku za korisnika. */
    suspend fun napraviKopiju(automatska: Boolean = false): String = zakljucaj.withLock {
        val mapa = _stanje.value.mapa ?: return "Prvo odaberite mapu za sigurnosne kopije."
        _stanje.value = _stanje.value.copy(radi = true)
        try {
            val json = uJson(procitajSve()).toString(1)
            val naziv = "SmartBudget_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) + ".json"
            val roditelj = DocumentsContract.buildDocumentUriUsingTree(mapa, DocumentsContract.getTreeDocumentId(mapa))
            val dat = DocumentsContract.createDocument(context.contentResolver, roditelj, "application/json", naziv)
                ?: error("Nije moguće stvoriti datoteku u odabranoj mapi.")
            context.contentResolver.openOutputStream(dat, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            prefs.edit().putLong("zadnji", System.currentTimeMillis()).putString("zadnjaDatoteka", naziv).remove("greska").apply()
            if (automatska) ocistiStare(mapa)
            "Sigurnosna kopija spremljena: $naziv"
        } catch (e: Exception) {
            val poruka = "Kopija nije uspjela: ${e.message ?: e.javaClass.simpleName}"
            prefs.edit().putString("greska", poruka).apply()
            poruka
        } finally {
            _stanje.value = ucitaj().copy(radi = false)
        }
    }

    /** Zadržava zadnjih [ZADRZI] kopija u mapi; starije briše. */
    private fun ocistiStare(mapa: Uri) = runCatching {
        val roditeljId = DocumentsContract.getTreeDocumentId(mapa)
        val djeca = DocumentsContract.buildChildDocumentsUriUsingTree(mapa, roditeljId)
        val kopije = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(
            djeca,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val ime = c.getString(1) ?: continue
                if (ime.startsWith("SmartBudget_backup_") && ime.endsWith(".json")) kopije += c.getString(0) to ime
            }
        }
        // Naziv sadrži datum i vrijeme, pa abecedni poredak = vremenski.
        kopije.sortedByDescending { it.second }.drop(ZADRZI).forEach { (id, _) ->
            DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(mapa, id))
        }
    }

    private suspend fun procitajSve(): SadrzajKopije {
        val db = repo.db
        return db.withTransaction {
            SadrzajKopije(
                vrijeme = Instant.now().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),
                verzijaAplikacije = BuildConfig.VERSION_NAME,
                kategorije = db.kategorije().sve(),
                transakcije = db.transakcije().sve(),
                ponavljajuci = db.ponavljajuci().sve(),
                postavke = repo.postavke(),
                prilagodbe = db.prilagodbe().sve(),
                racuni = db.racuni().sve(),
                ciljevi = db.ciljevi().sve(),
                uplate = db.uplate().sve(),
                novcanici = db.novcanici().sve(),
                promjeneNovcanika = db.novcanici().svePromjene(),
                postavkeAplikacije = AppPostavke.get(context).zaKopiju(),
                pozicije = db.pozicije().sve(),
                promjenePozicija = db.pozicije().svePromjene(),
            )
        }
    }

    /** Čita kopiju iz datoteke (bez promjene podataka), za pregled prije vraćanja. */
    fun procitajKopiju(uri: Uri): SadrzajKopije {
        val tekst = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        return izJsona(JSONObject(tekst))
    }

    /** Zamjenjuje sve podatke sadržajem kopije. */
    suspend fun vrati(k: SadrzajKopije) {
        val db = repo.db
        db.withTransaction {
            db.transakcije().obrisiSve()
            db.ponavljajuci().obrisiSve()
            db.kategorije().obrisiSve()
            db.kategorije().umetni(k.kategorije)
            db.transakcije().umetni(k.transakcije)
            db.ponavljajuci().umetni(k.ponavljajuci)
            db.postavke().spremi(k.postavke)
            db.prilagodbe().obrisiSve()
            db.prilagodbe().umetni(k.prilagodbe)
            // Kopije starijeg formata nemaju račune: zadržavaju se postojeći.
            if (k.racuni.isNotEmpty()) { db.racuni().obrisiSve(); db.racuni().umetni(k.racuni) }
            db.ciljevi().obrisiSve()
            db.ciljevi().umetni(k.ciljevi)
            db.uplate().obrisiSve()
            db.uplate().umetni(k.uplate)
            db.novcanici().obrisiSvePromjene()
            db.novcanici().obrisiSve()
            db.novcanici().umetni(k.novcanici)
            db.novcanici().umetniPromjene(k.promjeneNovcanika)
            db.pozicije().obrisiSvePromjene()
            db.pozicije().obrisiSve()
            db.pozicije().umetni(k.pozicije)
            db.pozicije().umetniPromjene(k.promjenePozicija)
        }
        k.postavkeAplikacije?.let { AppPostavke.get(context).vratiIzKopije(it) }
        hr.smartbudget.app.widget.StanjeWidget.osvjezi(context)
    }

    companion object {
        const val ZADRZI = 10
        /** 2: ručni rasponi ciklusa; 3: računi, ciljevi, prijenosi i podjele; 4: uplate na ciljeve; 5: novčanici u valutama; 6: prijenosi na štednju; 7: veza štednje na prijenos; 8: postavke aplikacije; 9: ulaganja; 10: ručno uneseni zapisi i obveznice; 11: upis dobiti zapisa (stariji formati se i dalje mogu vratiti). */
        private const val VERZIJA_FORMATA = 11

        @Volatile private var instance: BackupManager? = null
        fun get(context: Context): BackupManager = instance ?: synchronized(this) {
            instance ?: BackupManager(context.applicationContext).also { instance = it }
        }

        fun uJson(k: SadrzajKopije): JSONObject = JSONObject().apply {
            put("aplikacija", "SmartBudget")
            put("verzijaFormata", VERZIJA_FORMATA)
            put("verzijaAplikacije", k.verzijaAplikacije)
            put("vrijeme", k.vrijeme)
            put("postavke", JSONObject().apply {
                put("pocetniDanUCiklusu", k.postavke.pocetniDanUCiklusu)
                put("nacinObradeKrajaMjeseca", k.postavke.nacinObradeKrajaMjeseca)
            })
            put("kategorije", JSONArray(k.kategorije.map {
                JSONObject().apply {
                    put("id", it.id); put("naziv", it.naziv); put("bojaHex", it.bojaHex); put("ikonaNaziv", it.ikonaNaziv)
                    put("tip", it.tip.name); put("mjesecniLimitCenti", it.mjesecniLimitCenti ?: JSONObject.NULL)
                }
            }))
            put("transakcije", JSONArray(k.transakcije.map {
                JSONObject().apply {
                    put("id", it.id); put("tip", it.tip.name); put("iznosCenti", it.iznosCenti); put("datumVrijeme", it.datumVrijeme)
                    put("kategorijaId", it.kategorijaId); put("opis", it.opis); put("nacinPlacanja", it.nacinPlacanja)
                    put("ponavljajuciId", it.ponavljajuciId ?: JSONObject.NULL)
                    put("jePrijenos", it.jePrijenos); put("grupaId", it.grupaId ?: JSONObject.NULL); put("slika", it.slika ?: JSONObject.NULL)
                    put("naStednju", it.naStednju)
                }
            }))
            put("racuni", JSONArray(k.racuni.map {
                JSONObject().apply {
                    put("id", it.id); put("naziv", it.naziv); put("bojaHex", it.bojaHex); put("ikonaNaziv", it.ikonaNaziv)
                    put("pocetnoStanjeCenti", it.pocetnoStanjeCenti); put("redoslijed", it.redoslijed)
                }
            }))
            put("ciljevi", JSONArray(k.ciljevi.map {
                JSONObject().apply {
                    put("id", it.id); put("naziv", it.naziv); put("ciljCenti", it.ciljCenti); put("ustedjenoCenti", it.ustedjenoCenti)
                    put("rokEpochDan", it.rokEpochDan ?: JSONObject.NULL); put("bojaHex", it.bojaHex); put("ikonaNaziv", it.ikonaNaziv)
                    put("kreiranEpochDan", it.kreiranEpochDan ?: JSONObject.NULL)
                }
            }))
            put("uplateCilja", JSONArray(k.uplate.map {
                JSONObject().apply { put("id", it.id); put("ciljId", it.ciljId); put("iznosCenti", it.iznosCenti); put("epochDan", it.epochDan); put("valuta", it.valuta ?: JSONObject.NULL); put("grupaId", it.grupaId ?: JSONObject.NULL) }
            }))
            put("novcanici", JSONArray(k.novcanici.map {
                JSONObject().apply { put("id", it.id); put("naziv", it.naziv); put("valuta", it.valuta); put("redoslijed", it.redoslijed); put("rokEpochDan", it.rokEpochDan ?: JSONObject.NULL) }
            }))
            put("promjeneNovcanika", JSONArray(k.promjeneNovcanika.map {
                JSONObject().apply {
                    put("id", it.id); put("novcanikId", it.novcanikId); put("jedinice", it.jedinice); put("epochDan", it.epochDan)
                    put("ulozenoCenti", it.ulozenoCenti ?: JSONObject.NULL); put("napomena", it.napomena); put("grupaId", it.grupaId ?: JSONObject.NULL)
                }
            }))
            k.postavkeAplikacije?.let { put("postavkeAplikacije", it) }
            put("pozicije", JSONArray(k.pozicije.map {
                JSONObject().apply { put("id", it.id); put("simbol", it.simbol); put("naziv", it.naziv); put("vrsta", it.vrsta); put("valuta", it.valuta); put("redoslijed", it.redoslijed)
                    put("nominalCenti", it.nominalCenti ?: JSONObject.NULL); put("dospijeceEpochDan", it.dospijeceEpochDan ?: JSONObject.NULL)
                    put("dobitNa", it.dobitNa ?: JSONObject.NULL); put("dobitKada", it.dobitKada ?: JSONObject.NULL)
                    put("dobitGrupaId", it.dobitGrupaId ?: JSONObject.NULL); put("dobitKnjizena", it.dobitKnjizena)
                }
            }))
            put("promjenePozicija", JSONArray(k.promjenePozicija.map {
                JSONObject().apply {
                    put("id", it.id); put("pozicijaId", it.pozicijaId); put("kolicinaMikro", it.kolicinaMikro); put("epochDan", it.epochDan)
                    put("ulozenoCenti", it.ulozenoCenti ?: JSONObject.NULL); put("napomena", it.napomena)
                }
            }))
            put("prilagodbeCiklusa", JSONArray(k.prilagodbe.map {
                JSONObject().apply { put("sidro", it.sidro); put("pocetakEpochDan", it.pocetakEpochDan) }
            }))
            put("ponavljajuci", JSONArray(k.ponavljajuci.map {
                JSONObject().apply {
                    put("id", it.id); put("tip", it.tip.name); put("iznosCenti", it.iznosCenti); put("kategorijaId", it.kategorijaId)
                    put("opis", it.opis); put("nacinPlacanja", it.nacinPlacanja); put("danUMjesecu", it.danUMjesecu)
                    put("vrijemeMinuta", it.vrijemeMinuta); put("pocetakEpochDan", it.pocetakEpochDan)
                    put("zadnjiEpochDan", it.zadnjiEpochDan ?: JSONObject.NULL); put("aktivan", it.aktivan)
                }
            }))
        }

        fun izJsona(o: JSONObject): SadrzajKopije {
            require(o.optString("aplikacija") == "SmartBudget") { "Datoteka nije SmartBudget sigurnosna kopija." }
            require(o.optInt("verzijaFormata") in 1..VERZIJA_FORMATA) { "Kopija je iz novije verzije aplikacije." }
            fun JSONObject.dugiIliNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
            fun <T> JSONArray.mapiraj(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }
            val p = o.getJSONObject("postavke")
            return SadrzajKopije(
                vrijeme = o.optString("vrijeme"),
                verzijaAplikacije = o.optString("verzijaAplikacije"),
                postavke = PostavkeCiklusa(1, p.getInt("pocetniDanUCiklusu"), p.getBoolean("nacinObradeKrajaMjeseca")),
                kategorije = o.getJSONArray("kategorije").mapiraj {
                    Kategorija(
                        it.getLong("id"), it.getString("naziv"), it.getString("bojaHex"), it.getString("ikonaNaziv"),
                        TipTransakcije.valueOf(it.getString("tip")), it.dugiIliNull("mjesecniLimitCenti"),
                    )
                },
                transakcije = o.getJSONArray("transakcije").mapiraj {
                    Transakcija(
                        it.getLong("id"), TipTransakcije.valueOf(it.getString("tip")), it.getLong("iznosCenti"), it.getLong("datumVrijeme"),
                        it.getLong("kategorijaId"), it.optString("opis"), it.optString("nacinPlacanja"), it.dugiIliNull("ponavljajuciId"),
                        jePrijenos = it.optBoolean("jePrijenos", false), grupaId = it.dugiIliNull("grupaId"), naStednju = it.optBoolean("naStednju", false),
                        // Fotografije nisu u kopiji: putanja vrijedi samo ako datoteka postoji na ovom uređaju.
                        slika = if (it.isNull("slika") || !it.has("slika")) null else it.getString("slika").takeIf { s -> java.io.File(s).exists() },
                    )
                },
                racuni = (o.optJSONArray("racuni") ?: JSONArray()).mapiraj {
                    Racun(it.getLong("id"), it.getString("naziv"), it.getString("bojaHex"), it.getString("ikonaNaziv"), it.getLong("pocetnoStanjeCenti"), it.optInt("redoslijed"))
                },
                ciljevi = (o.optJSONArray("ciljevi") ?: JSONArray()).mapiraj {
                    CiljStednje(it.getLong("id"), it.getString("naziv"), it.getLong("ciljCenti"), it.getLong("ustedjenoCenti"), it.dugiIliNull("rokEpochDan"), it.getString("bojaHex"), it.getString("ikonaNaziv"), it.dugiIliNull("kreiranEpochDan"))
                },
                novcanici = (o.optJSONArray("novcanici") ?: JSONArray()).mapiraj {
                    Novcanik(it.getLong("id"), it.getString("naziv"), it.getString("valuta"), it.optInt("redoslijed"), it.dugiIliNull("rokEpochDan"))
                },
                promjeneNovcanika = (o.optJSONArray("promjeneNovcanika") ?: JSONArray()).mapiraj {
                    PromjenaNovcanika(it.getLong("id"), it.getLong("novcanikId"), it.getLong("jedinice"), it.getLong("epochDan"), it.dugiIliNull("ulozenoCenti"), it.optString("napomena"), it.dugiIliNull("grupaId"))
                },
                uplate = (o.optJSONArray("uplateCilja") ?: JSONArray()).mapiraj {
                    UplataCilja(it.getLong("id"), it.getLong("ciljId"), it.getLong("iznosCenti"), it.getLong("epochDan"), if (it.isNull("valuta") || !it.has("valuta")) null else it.getString("valuta"), it.dugiIliNull("grupaId"))
                },
                prilagodbe = (o.optJSONArray("prilagodbeCiklusa") ?: JSONArray()).mapiraj {
                    PrilagodbaCiklusa(it.getString("sidro"), it.getLong("pocetakEpochDan"))
                },
                ponavljajuci = o.getJSONArray("ponavljajuci").mapiraj {
                    PonavljajuciUnos(
                        it.getLong("id"), TipTransakcije.valueOf(it.getString("tip")), it.getLong("iznosCenti"), it.getLong("kategorijaId"),
                        it.optString("opis"), it.optString("nacinPlacanja"), it.getInt("danUMjesecu"), it.getInt("vrijemeMinuta"),
                        it.getLong("pocetakEpochDan"), it.dugiIliNull("zadnjiEpochDan"), it.getBoolean("aktivan"),
                    )
                },
                postavkeAplikacije = o.optJSONObject("postavkeAplikacije"),
                pozicije = (o.optJSONArray("pozicije") ?: JSONArray()).mapiraj {
                    Pozicija(it.getLong("id"), it.getString("simbol"), it.getString("naziv"), it.getString("vrsta"), it.getString("valuta"), it.optInt("redoslijed"), it.dugiIliNull("nominalCenti"), it.dugiIliNull("dospijeceEpochDan"),
                        if (it.isNull("dobitNa") || !it.has("dobitNa")) null else it.getString("dobitNa"),
                        if (it.isNull("dobitKada") || !it.has("dobitKada")) null else it.getString("dobitKada"),
                        it.dugiIliNull("dobitGrupaId"), it.optBoolean("dobitKnjizena", false))
                },
                promjenePozicija = (o.optJSONArray("promjenePozicija") ?: JSONArray()).mapiraj {
                    PromjenaPozicije(it.getLong("id"), it.getLong("pozicijaId"), it.getLong("kolicinaMikro"), it.getLong("epochDan"), it.dugiIliNull("ulozenoCenti"), it.optString("napomena"))
                },
            )
        }
    }
}
