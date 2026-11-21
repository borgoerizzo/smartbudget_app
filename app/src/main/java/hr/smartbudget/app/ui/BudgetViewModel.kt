package hr.smartbudget.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.data.PonavljajuciSKategorijom
import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.domain.GodisnjiPregled
import hr.smartbudget.app.domain.TjedniSazetak
import hr.smartbudget.app.domain.tjedniSazetak
import hr.smartbudget.app.domain.godisnjiPregled
import hr.smartbudget.app.domain.prognoza
import hr.smartbudget.app.data.PonavljajuciUnos
import hr.smartbudget.app.data.PostavkeCiklusa
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.Ciklus
import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.Ponavljanje
import hr.smartbudget.app.domain.Pretraga
import hr.smartbudget.app.domain.PromjenaKategorije
import hr.smartbudget.app.domain.StatusLimita
import hr.smartbudget.app.domain.StavkaPovijesti
import hr.smartbudget.app.domain.TockaTijeka
import hr.smartbudget.app.domain.UdioKategorije
import hr.smartbudget.app.domain.Zbroj
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.domain.jeStvarna
import hr.smartbudget.app.domain.naStednju
import hr.smartbudget.app.domain.najvecePromjene
import hr.smartbudget.app.domain.povijest
import hr.smartbudget.app.domain.predznaceniCenti
import hr.smartbudget.app.domain.raspodjelaTroskova
import hr.smartbudget.app.domain.statusLimita
import hr.smartbudget.app.domain.tijekNovca
import hr.smartbudget.app.domain.zbroj
import hr.smartbudget.app.export.FormatIzvoza
import hr.smartbudget.app.export.Izvoz
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

enum class FilterTransakcija(val naziv: String) { SVE("Sve"), TROSKOVI("Rashodi"), PRIHODI("Prihodi") }

data class GrupaDana(
    val datum: LocalDate,
    val naslov: String,
    val podnaslov: String,
    val neto: Long,
    val stavke: List<TransakcijaSKategorijom>,
)

data class PocetnaStanje(
    val ciklus: Ciklus,
    val jeAktivni: Boolean,
    val protekloDana: Int,
    val zbroj: Zbroj,
    val ukupnoStanje: Long,
    /** Minus s kraja prethodnog ciklusa koji se prenosi u ovaj (≤ 0); pozitivan ostatak se ne prenosi. */
    val prijenos: Long,
    /** Prosječni dnevni promjenjivi rashod (bez ponavljajućih unosa). */
    val dnevniProsjek: Long,
    val filter: FilterTransakcija,
    /** Tekst pretrage (prazno = bez pretrage). */
    val pretraga: String = "",
    /** Pretraga kroz sve cikluse. */
    val pretragaSvi: Boolean = false,
    /** Raspon ovog ciklusa je ručno promijenjen. */
    val prilagoden: Boolean = false,
    val grupe: List<GrupaDana>,
    val ucitano: Boolean,
    /** Sve transakcije ciklusa (bez filtra), za pregled prihoda/rashoda. */
    val transakcije: List<TransakcijaSKategorijom> = emptyList(),
) {
    val preostaloDana get() = ciklus.brojDana - protekloDana

    /** Neto prebačeno na štednju u ovom ciklusu (prijenosi na štednju − povlačenja natrag). */
    val naStednju: Long get() = transakcije.naStednju()

    /** Neto stanje ciklusa: prihodi − rashodi, umanjeno za preneseni minus i novac prebačen na štednju. */
    val neto: Long get() = zbroj.razlika + prijenos - naStednju

    /** Rashodi zajedno s prenesenim minusom (koliko je prihoda "pojedeno"). */
    private val opterecenje get() = zbroj.troskovi - prijenos
    val postoPotroseno: Double get() = if (zbroj.prihodi > 0) opterecenje * 100.0 / zbroj.prihodi else if (opterecenje > 0) 100.0 else 0.0
    val postoCiklusa: Double get() = protekloDana * 100.0 / ciklus.brojDana

    /** Isto upozorenje bez iznosa (kad je stanje skriveno). */
    val tempoBezIznosa: String?
        get() {
            if (!jeAktivni || preostaloDana <= 0 || dnevniProsjek <= 0) return null
            if (neto <= 0) return "Rashodi su premašili prihode ciklusa."
            val dostaje = (neto / dnevniProsjek).toInt()
            return if (dostaje < preostaloDana) "Pri trenutnom tempu sredstva dostaju za $dostaje od $preostaloDana preostalih dana."
            else "Pri trenutnom tempu sredstva dostaju do kraja ciklusa."
        }

    /** Upozorenje o tempu potrošnje; null kad nema smisla (prošli/budući ciklus ili bez troškova). */
    val tempo: Pair<String, Boolean>?
        get() {
            if (!jeAktivni || preostaloDana <= 0 || dnevniProsjek <= 0) return null
            val r = neto
            if (r <= 0) return "Rashodi su premašili prihode ciklusa za ${Novac.eur(-r)}." to true
            val dostaje = (r / dnevniProsjek).toInt()
            return if (dostaje < preostaloDana) {
                "Uz fiksne troškove već plaćene i tempo od ${Novac.eur(dnevniProsjek)}/dan, sredstva dostaju za $dostaje od $preostaloDana preostalih dana." to true
            } else {
                "Uz tempo od ${Novac.eur(dnevniProsjek)}/dan (bez fiksnih troškova) sredstva dostaju do kraja ciklusa." to false
            }
        }
}

data class AnalitikaStanje(
    val ciklus: Ciklus,
    val zbroj: Zbroj,
    val tijek: List<TockaTijeka>,
    val jeAktivni: Boolean,
    val raspodjela: List<UdioKategorije>,
    /** Transakcije ciklusa (za širenje kategorije u popis stavki). */
    val transakcije: List<TransakcijaSKategorijom> = emptyList(),
    val danUCiklusu: Int,
    val sadaDoDana: Zbroj,
    val prijeDoDana: Zbroj,
    val promjene: List<PromjenaKategorije>,
    val povijest: List<StavkaPovijesti>,
    val brojCiklusa: Int,
    /** Transakcije zadnjih N ciklusa i to razdoblje (za navike po danima i najčešće nazive). */
    val transakcijeRazdoblja: List<TransakcijaSKategorijom> = emptyList(),
    val razdobljeOd: LocalDate = LocalDate.now(),
    val razdobljeDo: LocalDate = LocalDate.now(),
)

data class BudzetKategorije(
    val kategorija: Kategorija,
    val potroseno: Long,
    val status: StatusLimita,
    /** 0–∞ (postotak limita), null bez limita. */
    val posto: Double?,
    /** Procjena potrošnje do kraja ciklusa (aktivni ciklus s limitom), inače null. */
    val prognoza: Long? = null,
)

data class StanjeRacunaUi(val racun: Racun, val stanje: Long)

data class BudzetiStanje(
    val ciklus: Ciklus,
    val troskovne: List<BudzetKategorije>,
    val prihodovne: List<BudzetKategorije>,
    val ukupniLimit: Long,
    val potrosenoULimitu: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
data class StanjeTecajeva(
    val hnb: hr.smartbudget.app.data.TecajnaLista? = null,
    val kripto: hr.smartbudget.app.data.KriptoCijene? = null,
    val ucitava: Boolean = false,
    val greska: String? = null,
) {
    /** Iznos u najmanjim jedinicama valute [iz] preračunat u valutu [u]; null bez tečaja. */
    fun pretvori(jedinice: Long, iz: hr.smartbudget.app.domain.Valuta, u: hr.smartbudget.app.domain.Valuta): Double? =
        hr.smartbudget.app.data.Tecajevi.pretvori(hr.smartbudget.app.domain.Valute.uBroj(jedinice, iz), iz, u, hnb, kripto)
}

/** Trenutne cijene ulaganja korisnika po simbolu. */
data class CijeneUlaganja(
    val kotacije: Map<String, hr.smartbudget.app.data.Kotacija> = emptyMap(),
    val ucitava: Boolean = false,
    val greska: String? = null,
    val dohvaceno: Long = 0,
)

/** Dnevne cijene ulaganja (u valuti kotacije) za graf. */
data class PovijestUlaganja(
    val cijene: Map<String, List<Pair<LocalDate, Double>>> = emptyMap(),
    val dana: Int = 90,
    val ucitava: Boolean = false,
    val greska: String? = null,
)

data class KriptoPovijest(
    val cijene: Map<String, List<Pair<LocalDate, Double>>> = emptyMap(),
    val dana: Int = 90,
    val ucitava: Boolean = false,
    val dohvaceno: Long = 0,
    val greska: String? = null,
)

class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BudgetRepository.get(app)
    private val tDao = repo.db.transakcije()
    private val kDao = repo.db.kategorije()
    private val pDao = repo.db.postavke()

    private val danas = MutableStateFlow(LocalDate.now())
    private val pomak = MutableStateFlow(0)
    private val filter = MutableStateFlow(FilterTransakcija.SVE)
    private val pretraga = MutableStateFlow("")
    /** Pretraga kroz sve cikluse umjesto samo kroz prikazani. */
    private val pretragaSvi = MutableStateFlow(false)
    private val filterIPretraga = combine(filter, pretraga, pretragaSvi) { f, q, svi -> Triple(f, q, svi) }
    private val brojCiklusa = MutableStateFlow(6)

    private val _poruke = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val poruke: SharedFlow<String> = _poruke

    val postavke: StateFlow<PostavkeCiklusa> = pDao.promatraj().map { it ?: PostavkeCiklusa() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PostavkeCiklusa())

    /** Izračun ciklusa: zadani dan u mjesecu + ručno promijenjeni rasponi pojedinih ciklusa. */
    val cycleManager: StateFlow<CycleManager> = combine(postavke, repo.db.prilagodbe().promatrajSve()) { p, pr ->
        CycleManager(p.pocetniDanUCiklusu, p.nacinObradeKrajaMjeseca, BudgetRepository.prilagodbeMapa(pr))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CycleManager(1))

    val ciklus: StateFlow<Ciklus> = combine(cycleManager, pomak, danas) { cm, o, d ->
        cm.ciklus(d, o)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CycleManager(1).ciklusZa(LocalDate.now()))

    /** Korisničke kategorije (bez skrivene sustavne kategorije prijenosa). */
    val kategorije: StateFlow<List<Kategorija>> = kDao.promatrajSve()
        .map { l -> l.filter { it.ikonaNaziv != BudgetRepository.KATEGORIJA_PRIJENOSA } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val racuni: StateFlow<List<Racun>> = repo.db.racuni().promatrajSve()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Stanje svakog računa: početno stanje + njegove transakcije. */
    val stanjaRacuna: StateFlow<List<StanjeRacunaUi>> = combine(racuni, tDao.promatrajPoRacunima()) { r, z ->
        val m = z.associate { it.nacinPlacanja to it.stanje }
        r.map { StanjeRacunaUi(it, it.pocetnoStanjeCenti + (m[it.naziv] ?: 0L)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ciljevi: StateFlow<List<CiljStednje>> = repo.db.ciljevi().promatrajSve()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Uplate na ciljeve, najnovije prve. */
    val uplate: StateFlow<List<hr.smartbudget.app.data.UplataCilja>> = repo.db.uplate().promatrajSve()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Novčanici i tečajevi ---

    val novcanici: StateFlow<List<hr.smartbudget.app.domain.StanjeNovcanika>> =
        combine(repo.db.novcanici().promatrajSve(), repo.db.novcanici().promatrajPromjene()) { n, p -> hr.smartbudget.app.domain.stanjaNovcanika(n, p) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tecajevi = MutableStateFlow(StanjeTecajeva())
    val tecajevi: StateFlow<StanjeTecajeva> = _tecajevi

    /** Svježi srednji tečaj HNB-a i cijene kriptovaluta (svaki put iznova; bez interneta zadnji spremljeni). */
    fun osvjeziTecajeve() {
        if (_tecajevi.value.ucitava) return
        // Dok stižu svježi tečajevi, preračun odmah koristi zadnje spremljene.
        if (_tecajevi.value.hnb == null || _tecajevi.value.kripto == null) {
            val (h, k) = hr.smartbudget.app.data.Tecajevi.spremljeno(getApplication())
            _tecajevi.value = _tecajevi.value.copy(hnb = _tecajevi.value.hnb ?: h, kripto = _tecajevi.value.kripto ?: k)
        }
        _tecajevi.value = _tecajevi.value.copy(ucitava = true, greska = null)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            // Usporedno: kriptovalute ne čekaju HNB (koji na nekim mrežama odgovara sporo).
            val hPosao = async { hr.smartbudget.app.data.Tecajevi.hnb(ctx) }
            val k = hr.smartbudget.app.data.Tecajevi.kripto(ctx)
            k.getOrNull()?.let { _tecajevi.value = _tecajevi.value.copy(kripto = it) }
            val h = hPosao.await()
            _tecajevi.value = StanjeTecajeva(
                hnb = h.getOrNull() ?: _tecajevi.value.hnb,
                kripto = k.getOrNull() ?: _tecajevi.value.kripto,
                ucitava = false,
                greska = if (h.isFailure || k.isFailure) "Tečajevi se ne mogu dohvatiti. Provjerite internetsku vezu." else null,
            )
        }
    }

    private val _kriptoPovijest = MutableStateFlow(KriptoPovijest())
    val kriptoPovijest: StateFlow<KriptoPovijest> = _kriptoPovijest

    /** Dnevne cijene kriptovaluta iz novčanika za zadnjih [dana] dana (CoinGecko). */
    fun osvjeziKriptoPovijest(dana: Int) {
        val kodovi = novcanici.value.map { it.valuta }.filter { it.kripto }.distinctBy { it.kod }
        if (kodovi.isEmpty() || _kriptoPovijest.value.ucitava) return
        _kriptoPovijest.value = _kriptoPovijest.value.copy(ucitava = true, greska = null, dana = dana)
        viewModelScope.launch {
            // Jedan po jedan, da se ne prijeđe ograničenje broja upita
            val ctx = getApplication<Application>()
            val rez = kodovi.associate { it.kod to hr.smartbudget.app.data.Tecajevi.povijest(ctx, it, dana) }
            _kriptoPovijest.value = KriptoPovijest(
                cijene = rez.mapNotNull { (k, r) -> r.getOrNull()?.let { k to it } }.toMap(),
                dana = dana, ucitava = false, dohvaceno = System.currentTimeMillis(),
                greska = if (rez.values.any { it.isFailure }) "Povijest cijena se ne može dohvatiti. Provjerite internetsku vezu." else null,
            )
        }
    }

    fun spremiNovcanik(n: hr.smartbudget.app.data.Novcanik) = repo.opseg.launch {
        repo.spremiNovcanik(n); _poruke.tryEmit(if (n.id == 0L) "Novčanik dodan" else "Novčanik spremljen"); runCatching { repo.provjeriRokove() }
    }
    fun obrisiNovcanik(n: hr.smartbudget.app.data.Novcanik) = repo.opseg.launch { repo.obrisiNovcanik(n); _poruke.tryEmit("Novčanik obrisan") }
    fun dodajPromjenu(p: hr.smartbudget.app.data.PromjenaNovcanika) = repo.opseg.launch { repo.dodajPromjenu(p) }
    fun obrisiPromjenu(p: hr.smartbudget.app.data.PromjenaNovcanika) = repo.opseg.launch { repo.obrisiPromjenu(p) }

    // --- Ulaganja u vlasništvu (dionice, ETF-ovi, obveznice, roba) ---

    val pozicije: StateFlow<List<hr.smartbudget.app.domain.StanjePozicije>> =
        combine(repo.db.pozicije().promatrajSve(), repo.db.pozicije().promatrajPromjene()) { p, pr -> hr.smartbudget.app.domain.Portfelj.stanja(p, pr) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _cijeneUlaganja = MutableStateFlow(CijeneUlaganja())
    val cijeneUlaganja: StateFlow<CijeneUlaganja> = _cijeneUlaganja

    /** Trenutne cijene (i ocjene analitičara) za sva ulaganja korisnika. */
    fun osvjeziCijeneUlaganja() {
        // Ručno uneseni zapisi nemaju tržišnu cijenu.
        val simboli = pozicije.value.filter { !it.pozicija.rucno }.map { it.pozicija.simbol }.distinct()
        if (simboli.isEmpty() || _cijeneUlaganja.value.ucitava) return
        _cijeneUlaganja.value = _cijeneUlaganja.value.copy(ucitava = true, greska = null)
        viewModelScope.launch {
            val r = runCatching { hr.smartbudget.app.data.Trziste.kotacijeUlaganja(simboli) }
            _cijeneUlaganja.value = CijeneUlaganja(
                kotacije = r.getOrNull()?.let { _cijeneUlaganja.value.kotacije + it } ?: _cijeneUlaganja.value.kotacije,
                ucitava = false,
                greska = r.exceptionOrNull()?.let { "Cijene se ne mogu dohvatiti. Provjerite internetsku vezu." },
                dohvaceno = if (r.isSuccess) System.currentTimeMillis() else _cijeneUlaganja.value.dohvaceno,
            )
        }
    }

    private val _povijestUlaganja = MutableStateFlow(PovijestUlaganja())
    val povijestUlaganja: StateFlow<PovijestUlaganja> = _povijestUlaganja

    /** Dnevne cijene ulaganja za graf u analitici. */
    fun osvjeziPovijestUlaganja(dana: Int) {
        val simboli = pozicije.value.filter { it.kolicinaMikro > 0 && !it.pozicija.rucno }.map { it.pozicija.simbol }.distinct()
        if (simboli.isEmpty() || _povijestUlaganja.value.ucitava) return
        _povijestUlaganja.value = _povijestUlaganja.value.copy(ucitava = true, greska = null, dana = dana)
        viewModelScope.launch {
            val rez = simboli.map { s -> async { s to runCatching { hr.smartbudget.app.data.Trziste.dnevnaPovijest(s, dana) } } }.awaitAll()
            _povijestUlaganja.value = PovijestUlaganja(
                cijene = rez.mapNotNull { (s, r) -> r.getOrNull()?.takeIf { it.isNotEmpty() }?.let { s to it } }.toMap(),
                dana = dana, ucitava = false,
                greska = if (rez.any { it.second.isFailure }) "Povijest cijena se ne može dohvatiti. Provjerite internetsku vezu." else null,
            )
        }
    }

    fun dodajPoziciju(p: hr.smartbudget.app.data.Pozicija, prva: hr.smartbudget.app.data.PromjenaPozicije?) = repo.opseg.launch {
        repo.dodajPoziciju(p, prva); _poruke.tryEmit("Ulaganje dodano: ${p.naziv}"); runCatching { repo.provjeriRokove() }
        kotlinx.coroutines.delay(300); osvjeziCijeneUlaganja()
    }
    fun spremiPoziciju(p: hr.smartbudget.app.data.Pozicija) = repo.opseg.launch {
        repo.spremiPoziciju(p); _poruke.tryEmit("Ulaganje spremljeno"); runCatching { repo.provjeriRokove() }
    }
    fun obrisiPoziciju(p: hr.smartbudget.app.data.Pozicija) = repo.opseg.launch { repo.obrisiPoziciju(p); _poruke.tryEmit("Ulaganje obrisano") }
    fun dodajPromjenuPozicije(p: hr.smartbudget.app.data.PromjenaPozicije) = repo.opseg.launch { repo.dodajPromjenuPozicije(p) }
    fun obrisiPromjenuPozicije(p: hr.smartbudget.app.data.PromjenaPozicije) = repo.opseg.launch { repo.obrisiPromjenuPozicije(p) }

    /** Podsjetnici na rok ulaganja (pri otvaranju aplikacije i nakon promjene roka). */
    fun provjeriRokove() = repo.opseg.launch { runCatching { repo.provjeriRokove() } }

    /**
     * Prodaja ili isplata iz štednje: zapis promjene i, ako je odabran račun, priljev na taj račun kao prijenos
     * sa štednje (vraća stanje ciklusa, ali se ne broji kao prihod). [racun] null = samo evidencija.
     */
    fun prodajaPozicije(p: hr.smartbudget.app.data.PromjenaPozicije, racun: String?, iznosCenti: Long?, opis: String) = repo.opseg.launch {
        repo.dodajPromjenuPozicije(p)
        if (racun != null && iznosCenti != null && iznosCenti > 0) {
            repo.prijenosSaStednje(racun, iznosCenti, System.currentTimeMillis(), opis, null, null, 0)
            _poruke.tryEmit("Isplaćeno na račun $racun")
        } else _poruke.tryEmit("Zabilježeno")
    }

    /** Isplata iz novčanika (valute, kripto) s opcionalnim priljevom na račun. */
    fun isplataNovcanika(p: hr.smartbudget.app.data.PromjenaNovcanika, racun: String?, iznosCenti: Long?, opis: String) = repo.opseg.launch {
        repo.dodajPromjenu(p)
        if (racun != null && iznosCenti != null && iznosCenti > 0) {
            repo.prijenosSaStednje(racun, iznosCenti, System.currentTimeMillis(), opis, null, null, 0)
            _poruke.tryEmit("Isplaćeno na račun $racun")
        }
    }

    /** Višak (prihodi − rashodi) prošlog ciklusa, kao prijedlog za uplatu na cilj; null ako ga nema. */
    val visakProslogCiklusa: StateFlow<Long?> = combine(cycleManager, danas) { cm, d -> cm.ciklusZaSidro(cm.ciklusZa(d).sidro.minusMonths(1)) }
        .flatMapLatest { p -> tDao.promatrajRaspon(p.odMillis(), p.doMillisIskljucivo()).map { tx -> tx.zbroj().razlika.takeIf { it > 0 } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Zadnji unosi za prijedloge naziva/kategorije/iznosa pri unosu. */
    val zadnjiUnosi: StateFlow<List<TransakcijaSKategorijom>> = tDao.promatrajZadnje()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val sveTransakcije = tDao.promatrajSve().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val transakcijeCiklusa = ciklus.flatMapLatest { tDao.promatrajRaspon(it.odMillis(), it.doMillisIskljucivo()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ukupnoStanje = tDao.promatrajUkupnoStanje().stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val stanjePrijeCiklusa = ciklus.flatMapLatest { tDao.promatrajStanjePrije(it.odMillis()) }

    /** Ukupno stanje i preneseni minus (stanje računa prije početka ciklusa, ako je negativno). */
    private val stanja = combine(ukupnoStanje, stanjePrijeCiklusa) { u, prije -> u to minOf(0L, prije) }

    val pocetna: StateFlow<PocetnaStanje> = combine(ciklus, transakcijeCiklusa, stanja, filterIPretraga, danas) { c, tx, (stanje, prijenos), (f, q, svi), d ->
        val lista = tx.orEmpty()
        val zaPopis = if (svi && q.isNotBlank()) sveTransakcije.value else lista
        val z = lista.zbroj()
        val proteklo = c.proteklihDana(d)
        val filtrirane = when (f) {
            FilterTransakcija.SVE -> zaPopis
            FilterTransakcija.TROSKOVI -> zaPopis.filter { it.jeStvarna && it.transakcija.tip == TipTransakcije.TROSAK }
            FilterTransakcija.PRIHODI -> zaPopis.filter { it.jeStvarna && it.transakcija.tip == TipTransakcije.PRIHOD }
        }.filter { Pretraga.odgovara(it, q) }
        val grupe = filtrirane.groupBy { it.datum() }.toSortedMap(compareByDescending { it }).map { (datum, stavke) ->
            GrupaDana(
                datum = datum,
                naslov = HrDatum.naslovDana(datum, d),
                podnaslov = HrDatum.sDanom(datum) + if (datum.year != d.year) " ${datum.year}." else "",
                neto = stavke.sumOf { it.predznaceniCenti },
                stavke = stavke,
            )
        }
        PocetnaStanje(
            ciklus = c,
            jeAktivni = d in c,
            protekloDana = proteklo,
            zbroj = z,
            ukupnoStanje = stanje,
            prijenos = prijenos,
            // Bez fiksnih (ponavljajućih) troškova: oni dolaze jednom u ciklusu i iskrivili bi dnevni tempo,
            // osobito prvih dana. Neto stanje ih već uključuje.
            dnevniProsjek = if (proteklo > 0) lista.filter {
                it.jeStvarna && it.transakcija.tip == TipTransakcije.TROSAK && it.transakcija.ponavljajuciId == null
            }.sumOf { it.transakcija.iznosCenti } / proteklo else 0,
            filter = f,
            pretraga = q,
            pretragaSvi = svi,
            prilagoden = cycleManager.value.jePrilagoden(c.sidro),
            grupe = grupe,
            ucitano = tx != null,
            transakcije = lista,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PocetnaStanje(ciklus.value, true, 1, Zbroj(), 0, 0, 0, FilterTransakcija.SVE, grupe = emptyList(), ucitano = false))

    /** Transakcije zadnjih N ciklusa (zaključno s odabranim) za povijest i usporedbu. */
    private val transakcijePovijesti = combine(ciklus, brojCiklusa, cycleManager) { c, n, cm ->
        (n - 1 downTo 0).map { cm.ciklusZaSidro(c.sidro.minusMonths(it.toLong())) }
    }.flatMapLatest { ciklusi ->
        tDao.promatrajRaspon(ciklusi.first().odMillis(), ciklusi.last().doMillisIskljucivo()).map { ciklusi to it }
    }

    val analitika: StateFlow<AnalitikaStanje?> = combine(
        ciklus, transakcijeCiklusa, transakcijePovijesti, stanjePrijeCiklusa, danas,
    ) { c, tx, (ciklusi, sve), pocetno, d ->
        val lista = tx.orEmpty()
        val dan = c.proteklihDana(d).coerceAtLeast(1)
        val prethodni = ciklusi.getOrNull(ciklusi.size - 2)
        val prijeLista = prethodni?.let { p ->
            val doDana = p.pocetak.plusDays(dan.toLong() - 1)
            sve.filter { val x = it.datum(); x in p && !x.isAfter(doDana) }
        }.orEmpty()
        val doDanaSada = c.pocetak.plusDays(dan.toLong() - 1)
        val sadaLista = lista.filter { !it.datum().isAfter(doDanaSada) }
        AnalitikaStanje(
            ciklus = c,
            zbroj = lista.zbroj(),
            tijek = tijekNovca(c, lista, pocetno, if (d in c) d else c.kraj),
            jeAktivni = d in c,
            raspodjela = raspodjelaTroskova(lista),
            transakcije = lista,
            danUCiklusu = dan,
            sadaDoDana = sadaLista.zbroj(),
            prijeDoDana = prijeLista.zbroj(),
            promjene = najvecePromjene(sadaLista, prijeLista),
            povijest = povijest(ciklusi, sve),
            brojCiklusa = ciklusi.size,
            transakcijeRazdoblja = sve,
            razdobljeOd = ciklusi.first().pocetak,
            razdobljeDo = minOf(d, ciklusi.last().kraj),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budzeti: StateFlow<BudzetiStanje> = combine(ciklus, kategorije, transakcijeCiklusa, danas) { c, kat, tx, d ->
        val poKat = tx.orEmpty().filter { it.jeStvarna }.groupBy { it.kategorija.id }.mapValues { (_, l) -> l.sumOf { it.transakcija.iznosCenti } }
        val proteklo = c.proteklihDana(d)
        fun stavka(k: Kategorija): BudzetKategorije {
            val p = poKat[k.id] ?: 0L
            val lim = k.mjesecniLimitCenti?.takeIf { it > 0 }
            val prog = if (lim != null && d in c && proteklo >= 3) prognoza(p, proteklo, c.brojDana) else null
            return BudzetKategorije(k, p, statusLimita(p, lim), lim?.let { p * 100.0 / it }, prog)
        }
        val troskovne = kat.filter { it.tip == TipTransakcije.TROSAK }.map(::stavka)
            .sortedWith(compareByDescending<BudzetKategorije> { it.posto ?: -1.0 }.thenByDescending { it.potroseno })
        val sLimitom = troskovne.filter { it.posto != null }
        BudzetiStanje(
            ciklus = c,
            troskovne = troskovne,
            prihodovne = kat.filter { it.tip == TipTransakcije.PRIHOD }.map(::stavka).sortedByDescending { it.potroseno },
            ukupniLimit = sLimitom.sumOf { it.kategorija.mjesecniLimitCenti ?: 0 },
            potrosenoULimitu = sLimitom.sumOf { it.potroseno },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BudzetiStanje(ciklus.value, emptyList(), emptyList(), 0, 0))

    val brojCiklusaPovijesti: StateFlow<Int> = brojCiklusa

    val ponavljajuci: StateFlow<List<PonavljajuciSKategorijom>> = repo.db.ponavljajuci().promatrajSve()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Radnje ---

    fun osvjeziDanas() { danas.value = LocalDate.now() }
    fun prethodniCiklus() { pomak.value -= 1 }
    fun sljedeciCiklus() { pomak.value += 1 }
    fun trenutniCiklus() { pomak.value = 0 }
    fun postaviFilter(f: FilterTransakcija) { filter.value = f }
    fun postaviPretragu(q: String) { pretraga.value = q }
    fun postaviPretraguSvi(svi: Boolean) { pretragaSvi.value = svi }

    /** Tjedni pregled: zadnjih 7 dana i usporedba s prethodnih 7. */
    val tjedni: StateFlow<TjedniSazetak?> = combine(sveTransakcije, danas) { sve, d -> tjedniSazetak(sve, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Godišnji pregled ---
    private val godina = MutableStateFlow(LocalDate.now().year)
    val godisnji: StateFlow<GodisnjiPregled?> = combine(sveTransakcije, godina) { sve, g -> godisnjiPregled(sve, g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun postaviGodinu(g: Int) { godina.value = g }

    // --- Računi, prijenosi, podjele, ciljevi ---
    fun spremiRacun(r: Racun, stariNaziv: String?) = repo.opseg.launch {
        repo.spremiRacun(r, stariNaziv)
        _poruke.tryEmit(if (r.id == 0L) "Račun dodan" else "Račun spremljen")
    }

    fun obrisiRacun(r: Racun) = repo.opseg.launch { _poruke.tryEmit(repo.obrisiRacun(r) ?: "Račun obrisan") }

    fun prijenosNaStednju(
        s: String, iznosCenti: Long, kada: Long, opis: String,
        cilj: CiljStednje?, novcanik: hr.smartbudget.app.data.Novcanik?, jedinice: Long,
    ) = repo.opseg.launch {
        repo.prijenosNaStednju(s, iznosCenti, kada, opis, cilj, novcanik, jedinice)
        _poruke.tryEmit("Prijenos ${Novac.eur(iznosCenti)}: $s → ${cilj?.naziv ?: novcanik?.naziv}")
    }

    fun prijenosSaStednje(
        na: String, iznosCenti: Long, kada: Long, opis: String,
        cilj: CiljStednje?, novcanik: hr.smartbudget.app.data.Novcanik?, jedinice: Long,
    ) = repo.opseg.launch {
        repo.prijenosSaStednje(na, iznosCenti, kada, opis, cilj, novcanik, jedinice)
        _poruke.tryEmit("Prijenos ${Novac.eur(iznosCenti)}: ${cilj?.naziv ?: novcanik?.naziv} → $na")
    }

    fun prijenos(s: String, na: String, iznosCenti: Long, kada: Long, opis: String) = repo.opseg.launch {
        repo.prijenos(s, na, iznosCenti, kada, opis)
        _poruke.tryEmit("Prijenos ${Novac.eur(iznosCenti)}: $s → $na")
    }

    fun spremiPodijeljeno(dijelovi: List<Transakcija>) = repo.opseg.launch {
        repo.spremiPodijeljeno(dijelovi).forEach { _poruke.tryEmit(it) }
    }

    fun spremiCilj(c: CiljStednje) = repo.opseg.launch {
        repo.spremiCilj(c)
        _poruke.tryEmit(if (c.id == 0L) "Cilj štednje dodan" else "Cilj spremljen")
    }

    fun obrisiUplatuCilja(u: hr.smartbudget.app.data.UplataCilja) = repo.opseg.launch {
        repo.obrisiUplatuCilja(u); _poruke.tryEmit("Unos obrisan")
    }

    fun uplatiNaCiljUValuti(c: CiljStednje, jedinice: Long, valuta: hr.smartbudget.app.domain.Valuta) = repo.opseg.launch {
        repo.uplatiNaCiljUValuti(c, jedinice, valuta.kod)
        val t = hr.smartbudget.app.domain.Valute.iznos(kotlin.math.abs(jedinice), valuta)
        _poruke.tryEmit(if (jedinice >= 0) "Uplaćeno $t na \"${c.naziv}\"" else "Povučeno $t s \"${c.naziv}\"")
    }

    fun uplatiNaCilj(c: CiljStednje, iznosCenti: Long) = repo.opseg.launch {
        repo.uplatiNaCilj(c, iznosCenti)
        _poruke.tryEmit(if (iznosCenti >= 0) "Uplaćeno ${Novac.eur(iznosCenti)} na \"${c.naziv}\"" else "Povučeno ${Novac.eur(-iznosCenti)} s \"${c.naziv}\"")
    }

    fun obrisiCilj(c: CiljStednje) = repo.opseg.launch { repo.obrisiCilj(c); _poruke.tryEmit("Cilj obrisan") }
    fun postaviBrojCiklusa(n: Int) { brojCiklusa.value = n }

    /** Nakon promjene valute: widget odmah prikazuje novu oznaku. */
    fun osvjeziWidget() = repo.opseg.launch { hr.smartbudget.app.widget.StanjeWidget.osvjezi(repo.context) }

    // Upisi idu kroz repozitorij u opsegu procesa, pa završe i ako se zaslon zatvori.
    fun spremiTransakciju(t: Transakcija) = repo.opseg.launch {
        repo.spremiTransakciju(t).forEach { _poruke.tryEmit(it) }
    }

    fun obrisiTransakciju(t: Transakcija) = repo.opseg.launch {
        val stednja = repo.obrisiTransakciju(t)
        _poruke.tryEmit(if (stednja) "Prijenos obrisan i poništen na štednji" else "Transakcija obrisana")
    }

    /** Brisanje odabranih unosa (višestruki odabir). */
    fun obrisiTransakcije(lista: List<Transakcija>) = repo.opseg.launch {
        if (lista.isEmpty()) return@launch
        if (lista.size == 1) {
            val stednja = repo.obrisiTransakciju(lista.first())
            _poruke.tryEmit(if (stednja) "Prijenos obrisan i poništen na štednji" else "Transakcija obrisana")
            return@launch
        }
        val stednja = repo.obrisiTransakcije(lista)
        _poruke.tryEmit("Obrisano: ${lista.size} ${unosaRijec(lista.size)}" + if (stednja > 0) " (štednja poništena)" else "")
    }

    private fun unosaRijec(n: Int) = if (n % 10 == 1 && n % 100 != 11) "unos" else "unosa"

    fun spremiKategoriju(k: Kategorija) = repo.opseg.launch {
        repo.spremiKategoriju(k)
        _poruke.tryEmit(if (k.id == 0L) "Kategorija dodana" else "Kategorija spremljena")
    }

    fun obrisiKategoriju(k: Kategorija) = repo.opseg.launch {
        _poruke.tryEmit(repo.obrisiKategoriju(k) ?: "Kategorija obrisana")
    }

    fun spremiPonavljajuci(p: PonavljajuciUnos) = repo.opseg.launch {
        val novih = repo.spremiPonavljajuci(p)
        _poruke.tryEmit(
            (if (p.id == 0L) "Ponavljajući unos dodan" else "Ponavljajući unos spremljen") +
                if (novih > 0) " · stvoreno unosa: $novih" else " · prvi unos ${HrDatum.kratko(Ponavljanje.sljedeciDatum(p))}",
        )
    }

    fun obrisiPonavljajuci(p: PonavljajuciUnos) = repo.opseg.launch {
        repo.obrisiPonavljajuci(p)
        _poruke.tryEmit("Ponavljajući unos obrisan (već stvoreni unosi ostaju)")
    }

    fun postaviPocetniDan(dan: Int) = repo.opseg.launch {
        repo.spremiPostavke(postavke.value.copy(pocetniDanUCiklusu = dan.coerceIn(1, 31)))
        pomak.value = 0
    }

    fun postaviKrajMjeseca(zadnjiDan: Boolean) = repo.opseg.launch {
        repo.spremiPostavke(postavke.value.copy(nacinObradeKrajaMjeseca = zadnjiDan))
        pomak.value = 0
    }

    /** Ciklus pomaknut za [pomakOdTrenutnog] u odnosu na današnji (za izvoz "Prošli ciklus"). */
    fun ciklusOdDanas(pomakOdTrenutnog: Int): Ciklus = cycleManager.value.ciklus(LocalDate.now(), pomakOdTrenutnog)

    /** Ručni raspon prikazanog ciklusa (npr. 9. 10. – 9. 11. umjesto zadanog 10. 10. – 9. 11.). */
    fun postaviRasponCiklusa(od: LocalDate, doDana: LocalDate) = repo.opseg.launch {
        val c = ciklus.value
        val greska = repo.postaviRasponCiklusa(c.sidro, od, doDana)
        _poruke.tryEmit(greska ?: "Raspon ciklusa: ${HrDatum.kratko(od)} – ${HrDatum.kratko(doDana)}")
    }

    fun vratiZadaniRaspon() = repo.opseg.launch {
        repo.vratiZadaniRaspon(ciklus.value.sidro)
        _poruke.tryEmit("Ciklus vraćen na zadani raspon")
    }

    suspend fun izvezi(
        format: FormatIzvoza,
        od: LocalDate,
        doDana: LocalDate,
        kategorijeId: Set<Long>,
        out: OutputStream,
    ): Int {
        val zona = ZoneId.systemDefault()
        val lista = tDao.dohvatiRaspon(od.atStartOfDay(zona).toInstant().toEpochMilli(), doDana.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli())
            .filter { it.kategorija.id in kategorijeId }
        Izvoz.zapisi(format, lista, out)
        return lista.size
    }

    fun poruka(tekst: String) { _poruke.tryEmit(tekst) }
}
