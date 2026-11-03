package hr.smartbudget.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.PonavljajuciSKategorijom
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
import hr.smartbudget.app.domain.PromjenaKategorije
import hr.smartbudget.app.domain.StatusLimita
import hr.smartbudget.app.domain.StavkaPovijesti
import hr.smartbudget.app.domain.TockaTijeka
import hr.smartbudget.app.domain.UdioKategorije
import hr.smartbudget.app.domain.Zbroj
import hr.smartbudget.app.domain.datum
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
    val dnevniProsjek: Long,
    val filter: FilterTransakcija,
    val grupe: List<GrupaDana>,
    val ucitano: Boolean,
) {
    val preostaloDana get() = ciklus.brojDana - protekloDana

    /** Neto stanje ciklusa: prihodi − rashodi, umanjeno za preneseni minus. */
    val neto: Long get() = zbroj.razlika + prijenos

    /** Rashodi zajedno s prenesenim minusom (koliko je prihoda "pojedeno"). */
    private val opterecenje get() = zbroj.troskovi - prijenos
    val postoPotroseno: Double get() = if (zbroj.prihodi > 0) opterecenje * 100.0 / zbroj.prihodi else if (opterecenje > 0) 100.0 else 0.0
    val postoCiklusa: Double get() = protekloDana * 100.0 / ciklus.brojDana

    /** Upozorenje o tempu potrošnje; null kad nema smisla (prošli/budući ciklus ili bez troškova). */
    val tempo: Pair<String, Boolean>?
        get() {
            if (!jeAktivni || preostaloDana <= 0 || dnevniProsjek <= 0) return null
            val r = neto
            if (r <= 0) return "Rashodi su premašili prihode ciklusa za ${Novac.eur(-r)}." to true
            val dostaje = (r / dnevniProsjek).toInt()
            return if (dostaje < preostaloDana) {
                "Pri tempu od ${Novac.eur(dnevniProsjek)}/dan sredstva dostaju za $dostaje od $preostaloDana preostalih dana." to true
            } else {
                "Pri tempu od ${Novac.eur(dnevniProsjek)}/dan sredstva dostaju do kraja ciklusa." to false
            }
        }
}

data class AnalitikaStanje(
    val ciklus: Ciklus,
    val zbroj: Zbroj,
    val tijek: List<TockaTijeka>,
    val jeAktivni: Boolean,
    val raspodjela: List<UdioKategorije>,
    val danUCiklusu: Int,
    val sadaDoDana: Zbroj,
    val prijeDoDana: Zbroj,
    val promjene: List<PromjenaKategorije>,
    val povijest: List<StavkaPovijesti>,
    val brojCiklusa: Int,
)

data class BudzetKategorije(
    val kategorija: Kategorija,
    val potroseno: Long,
    val status: StatusLimita,
    /** 0–∞ (postotak limita), null bez limita. */
    val posto: Double?,
)

data class BudzetiStanje(
    val ciklus: Ciklus,
    val troskovne: List<BudzetKategorije>,
    val prihodovne: List<BudzetKategorije>,
    val ukupniLimit: Long,
    val potrosenoULimitu: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BudgetRepository.get(app)
    private val tDao = repo.db.transakcije()
    private val kDao = repo.db.kategorije()
    private val pDao = repo.db.postavke()

    private val danas = MutableStateFlow(LocalDate.now())
    private val pomak = MutableStateFlow(0)
    private val filter = MutableStateFlow(FilterTransakcija.SVE)
    private val brojCiklusa = MutableStateFlow(6)

    private val _poruke = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val poruke: SharedFlow<String> = _poruke

    val postavke: StateFlow<PostavkeCiklusa> = pDao.promatraj().map { it ?: PostavkeCiklusa() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PostavkeCiklusa())

    val ciklus: StateFlow<Ciklus> = combine(postavke, pomak, danas) { p, o, d ->
        CycleManager(p.pocetniDanUCiklusu, p.nacinObradeKrajaMjeseca).ciklus(d, o)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CycleManager(1).ciklusZa(LocalDate.now()))

    val kategorije: StateFlow<List<Kategorija>> = kDao.promatrajSve()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val transakcijeCiklusa = ciklus.flatMapLatest { tDao.promatrajRaspon(it.odMillis(), it.doMillisIskljucivo()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ukupnoStanje = tDao.promatrajUkupnoStanje().stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val stanjePrijeCiklusa = ciklus.flatMapLatest { tDao.promatrajStanjePrije(it.odMillis()) }

    /** Ukupno stanje i preneseni minus (stanje računa prije početka ciklusa, ako je negativno). */
    private val stanja = combine(ukupnoStanje, stanjePrijeCiklusa) { u, prije -> u to minOf(0L, prije) }

    val pocetna: StateFlow<PocetnaStanje> = combine(ciklus, transakcijeCiklusa, stanja, filter, danas) { c, tx, (stanje, prijenos), f, d ->
        val lista = tx.orEmpty()
        val z = lista.zbroj()
        val proteklo = c.proteklihDana(d)
        val filtrirane = when (f) {
            FilterTransakcija.SVE -> lista
            FilterTransakcija.TROSKOVI -> lista.filter { it.transakcija.tip == TipTransakcije.TROSAK }
            FilterTransakcija.PRIHODI -> lista.filter { it.transakcija.tip == TipTransakcije.PRIHOD }
        }
        val grupe = filtrirane.groupBy { it.datum() }.toSortedMap(compareByDescending { it }).map { (datum, stavke) ->
            GrupaDana(
                datum = datum,
                naslov = HrDatum.naslovDana(datum, d),
                podnaslov = HrDatum.sDanom(datum),
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
            dnevniProsjek = if (proteklo > 0) z.troskovi / proteklo else 0,
            filter = f,
            grupe = grupe,
            ucitano = tx != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PocetnaStanje(ciklus.value, true, 1, Zbroj(), 0, 0, 0, FilterTransakcija.SVE, emptyList(), false))

    /** Transakcije zadnjih N ciklusa (zaključno s odabranim) za povijest i usporedbu. */
    private val transakcijePovijesti = combine(ciklus, brojCiklusa, postavke) { c, n, p ->
        val cm = CycleManager(p.pocetniDanUCiklusu, p.nacinObradeKrajaMjeseca)
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
            danUCiklusu = dan,
            sadaDoDana = sadaLista.zbroj(),
            prijeDoDana = prijeLista.zbroj(),
            promjene = najvecePromjene(sadaLista, prijeLista),
            povijest = povijest(ciklusi, sve),
            brojCiklusa = ciklusi.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budzeti: StateFlow<BudzetiStanje> = combine(ciklus, kategorije, transakcijeCiklusa) { c, kat, tx ->
        val poKat = tx.orEmpty().groupBy { it.kategorija.id }.mapValues { (_, l) -> l.sumOf { it.transakcija.iznosCenti } }
        fun stavka(k: Kategorija): BudzetKategorije {
            val p = poKat[k.id] ?: 0L
            val lim = k.mjesecniLimitCenti?.takeIf { it > 0 }
            return BudzetKategorije(k, p, statusLimita(p, lim), lim?.let { p * 100.0 / it })
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
    fun postaviBrojCiklusa(n: Int) { brojCiklusa.value = n }

    // Upisi idu kroz repozitorij u opsegu procesa, pa završe i ako se zaslon zatvori.
    fun spremiTransakciju(t: Transakcija) = repo.opseg.launch {
        repo.spremiTransakciju(t).forEach { _poruke.tryEmit(it) }
    }

    fun obrisiTransakciju(t: Transakcija) = repo.opseg.launch {
        repo.obrisiTransakciju(t)
        _poruke.tryEmit("Transakcija obrisana")
    }

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
    fun ciklusOdDanas(pomakOdTrenutnog: Int): Ciklus {
        val p = postavke.value
        return CycleManager(p.pocetniDanUCiklusu, p.nacinObradeKrajaMjeseca).ciklus(LocalDate.now(), pomakOdTrenutnog)
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
