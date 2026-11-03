package hr.smartbudget.app.data

import android.content.Context
import hr.smartbudget.app.domain.Ciklus
import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.PRAG_UPOZORENJA
import hr.smartbudget.app.domain.Ponavljanje
import hr.smartbudget.app.domain.prognoza
import hr.smartbudget.app.domain.TjedniSazetak
import hr.smartbudget.app.domain.tjedniSazetak
import hr.smartbudget.app.domain.prognozaPrekoracenja
import hr.smartbudget.app.domain.Zbroj
import hr.smartbudget.app.domain.zbroj
import hr.smartbudget.app.widget.StanjeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Zajedničko mjesto za upis podataka (aplikacija i brzi unos s widgeta).
 * Nakon svake promjene osvježava widget na početnom zaslonu.
 */
class BudgetRepository private constructor(private val context: Context) {
    val db = BudgetDatabase.get(context)
    private val tDao = db.transakcije()
    private val kDao = db.kategorije()
    private val pDao = db.postavke()
    private val rDao = db.ponavljajuci()
    private val generiranje = Mutex()

    /** Opseg procesa: upis završava i kad se zaslon (npr. brzi unos) zatvori odmah nakon spremanja. */
    val opseg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun postavke(): PostavkeCiklusa = pDao.dohvati() ?: PostavkeCiklusa()

    /** Izračun ciklusa iz postavki i ručnih prilagodbi raspona. */
    suspend fun cycleManager(): CycleManager =
        postavke().let { CycleManager(it.pocetniDanUCiklusu, it.nacinObradeKrajaMjeseca, prilagodbeMapa(db.prilagodbe().sve())) }

    suspend fun ciklusZa(datum: LocalDate): Ciklus = cycleManager().ciklusZa(datum)

    /**
     * Ručni raspon ciklusa [sidro]: početak se sprema za taj ciklus, a kraj kao početak sljedećeg.
     * Vrijednost jednaka zadanoj se ne sprema (briše prilagodbu). Vraća poruku o grešci ili null.
     */
    suspend fun postaviRasponCiklusa(sidro: YearMonth, od: LocalDate, doDana: LocalDate): String? {
        val cm = cycleManager()
        cm.provjeriRaspon(sidro, od, doDana)?.let { return it }
        val pDao = db.prilagodbe()
        db.withTransaction {
            suspend fun spremi(m: YearMonth, pocetak: LocalDate) = if (pocetak == cm.zadaniPocetak(m)) pDao.obrisi(m.toString())
            else pDao.spremi(PrilagodbaCiklusa(m.toString(), pocetak.toEpochDay()))
            spremi(sidro, od)
            spremi(sidro.plusMonths(1), doDana.plusDays(1))
        }
        promjena()
        return null
    }

    /** Vraća ciklus [sidro] na zadani raspon (briše prilagodbu početka i kraja). */
    suspend fun vratiZadaniRaspon(sidro: YearMonth) {
        db.prilagodbe().obrisi(sidro.toString())
        db.prilagodbe().obrisi(sidro.plusMonths(1).toString())
        promjena()
    }

    /** Sprema transakciju; vraća poruke za korisnika (potvrda i eventualno upozorenje o limitu). */
    suspend fun spremiTransakciju(t: Transakcija): List<String> {
        val kat = db.kategorije().dohvati(t.kategorijaId)
        val limit = kat?.mjesecniLimitCenti?.takeIf { it > 0 && kat.tip == TipTransakcije.TROSAK }
        val c = ciklusZa(Instant.ofEpochMilli(t.datumVrijeme).atZone(ZoneId.systemDefault()).toLocalDate())
        suspend fun potroseno() = if (limit == null) 0L else tDao.dohvatiRaspon(c.odMillis(), c.doMillisIskljucivo())
            .filter { it.kategorija.id == t.kategorijaId && it.transakcija.tip == TipTransakcije.TROSAK }
            .sumOf { it.transakcija.iznosCenti }
        val prije = potroseno()
        tDao.spremi(t)
        val poruke = mutableListOf(if (t.id == 0L) "Transakcija dodana" else "Transakcija spremljena")
        if (limit != null) {
            val poslije = potroseno()
            val prag = (limit * PRAG_UPOZORENJA).toLong()
            when {
                prije < limit && poslije >= limit ->
                    poruke += "Prekoračen limit: ${kat.naziv} (${Novac.eur(poslije)} od ${Novac.eur(limit)})"
                prije < prag && poslije >= prag ->
                    poruke += "Upozorenje: ${kat.naziv} je na ${poslije * 100 / limit} % limita"
            }
        }
        promjena()
        return poruke
    }

    /** Brisanje; dio prijenosa briše i drugi dio (inače bi stanja računa bila kriva). */
    suspend fun obrisiTransakciju(t: Transakcija) {
        if (t.jePrijenos && t.grupaId != null) tDao.obrisiGrupu(t.grupaId) else tDao.obrisi(t)
        t.slika?.let { runCatching { java.io.File(it).delete() } }
        promjena()
    }

    // --- Računi i prijenosi ---

    /** Skrivena sustavna kategorija za prijenose (ne prikazuje se u popisima kategorija). */
    suspend fun kategorijaPrijenosa(): Long =
        kDao.sve().firstOrNull { it.ikonaNaziv == KATEGORIJA_PRIJENOSA }?.id
            ?: kDao.spremi(Kategorija(naziv = "Prijenos", bojaHex = "#9AA7B8", ikonaNaziv = KATEGORIJA_PRIJENOSA, tip = TipTransakcije.TROSAK))

    /** Prijenos s računa [s] na račun [na]: dva povezana unosa koji se ne broje u prihode/troškove. */
    suspend fun prijenos(s: String, na: String, iznosCenti: Long, kada: Long, opis: String) {
        val kat = kategorijaPrijenosa()
        val grupa = System.currentTimeMillis()
        val naziv = opis.ifBlank { "Prijenos $s → $na" }
        db.withTransaction {
            tDao.spremi(Transakcija(tip = TipTransakcije.TROSAK, iznosCenti = iznosCenti, datumVrijeme = kada, kategorijaId = kat, opis = naziv, nacinPlacanja = s, jePrijenos = true, grupaId = grupa))
            tDao.spremi(Transakcija(tip = TipTransakcije.PRIHOD, iznosCenti = iznosCenti, datumVrijeme = kada, kategorijaId = kat, opis = naziv, nacinPlacanja = na, jePrijenos = true, grupaId = grupa))
        }
        promjena()
    }

    /** Sprema račun; kod promjene naziva preimenuje ga i u svim njegovim transakcijama. */
    suspend fun spremiRacun(r: Racun, stariNaziv: String?) {
        db.withTransaction {
            if (stariNaziv != null && stariNaziv != r.naziv) tDao.preimenujRacun(stariNaziv, r.naziv)
            db.racuni().spremi(r)
        }
        promjena()
    }

    /** Vraća poruku o grešci ako račun ima transakcija, inače null. */
    suspend fun obrisiRacun(r: Racun): String? {
        val n = tDao.brojZaRacun(r.naziv)
        if (n > 0) return "Račun \"${r.naziv}\" ima $n transakcija i ne može se obrisati."
        db.racuni().obrisi(r)
        promjena()
        return null
    }

    /** Podijeljeni trošak: više transakcija (po kategoriji) s istim nazivom, datumom i računom. */
    suspend fun spremiPodijeljeno(dijelovi: List<Transakcija>): List<String> {
        val grupa = System.currentTimeMillis()
        db.withTransaction { dijelovi.forEach { tDao.spremi(it.copy(grupaId = grupa)) } }
        promjena()
        return listOf("Spremljeno: ${dijelovi.size} dijela, ukupno ${Novac.eur(dijelovi.sumOf { it.iznosCenti })}")
    }

    // --- Uvoz izvoda ---

    /** Jedan redak izvoda: iznos s predznakom (minus = trošak). */
    data class UvozRed(val datum: LocalDate, val centi: Long, val opis: String)

    /** Uvozi retke; isti dan + iznos + tip koji već postoji smatra se duplikatom. Vraća (uvezeno, duplikata). */
    suspend fun uvezi(redovi: List<UvozRed>, racun: String, kategorijaTrosak: Long, kategorijaPrihod: Long): Pair<Int, Int> {
        val zona = ZoneId.systemDefault()
        var uvezeno = 0
        var duplikati = 0
        db.withTransaction {
            redovi.forEach { r ->
                val tip = if (r.centi < 0) TipTransakcije.TROSAK else TipTransakcije.PRIHOD
                val iznos = kotlin.math.abs(r.centi)
                val od = r.datum.atStartOfDay(zona).toInstant().toEpochMilli()
                val doD = r.datum.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
                if (iznos == 0L) return@forEach
                if (tDao.brojIstih(od, doD, iznos, tip) > 0) { duplikati++; return@forEach }
                tDao.spremi(
                    Transakcija(
                        tip = tip, iznosCenti = iznos, datumVrijeme = od + 12 * 3_600_000L,
                        kategorijaId = if (tip == TipTransakcije.TROSAK) kategorijaTrosak else kategorijaPrihod,
                        opis = r.opis.take(120), nacinPlacanja = racun,
                    ),
                )
                uvezeno++
            }
        }
        if (uvezeno > 0) promjena()
        return uvezeno to duplikati
    }

    // --- Ciljevi štednje ---

    suspend fun spremiCilj(c: CiljStednje) = db.ciljevi().spremi(c)
    suspend fun obrisiCilj(c: CiljStednje) = db.ciljevi().obrisi(c)

    suspend fun spremiKategoriju(k: Kategorija) {
        kDao.spremi(k)
        promjena()
    }

    /** Vraća poruku o grešci ako kategorija ima transakcija ili ponavljajućih unosa, inače null. */
    suspend fun obrisiKategoriju(k: Kategorija): String? {
        val n = tDao.brojZaKategoriju(k.id)
        if (n > 0) return "Kategorija \"${k.naziv}\" ima $n transakcija i ne može se obrisati."
        val r = rDao.brojZaKategoriju(k.id)
        if (r > 0) return "Kategoriju \"${k.naziv}\" koristi $r ponavljajućih unosa i ne može se obrisati."
        kDao.obrisi(k)
        promjena()
        return null
    }

    suspend fun spremiPostavke(p: PostavkeCiklusa) {
        pDao.spremi(p)
        promjena()
    }

    /** Sprema ponavljajući unos i odmah stvara unose koji su već dospjeli (npr. ako je prvi datum danas). */
    suspend fun spremiPonavljajuci(p: PonavljajuciUnos): Int {
        rDao.spremi(p)
        // Korisnik upravo unosi pravilo u aplikaciji: bez obavijesti o "naplati".
        val novih = generirajPonavljajuce(obavijesti = false)
        if (novih == 0) promjena()
        return novih
    }

    suspend fun obrisiPonavljajuci(p: PonavljajuciUnos) {
        rDao.obrisi(p)
        promjena()
    }

    /** Nakon svake promjene podataka: osvježi widget i (ako je uključeno) zakaži sigurnosnu kopiju. */
    /** Prognoza limita za aktivni ciklus: obavijest kad tempo vodi preko limita (jednom po kategoriji u ciklusu). */
    suspend fun provjeriPrognoze() {
        val postavke = AppPostavke.get(context)
        if (!postavke.stanje.value.obavijestPrognoza) return
        val danas = LocalDate.now()
        val c = cycleManager().ciklusZa(danas)
        val proteklo = c.proteklihDana(danas)
        val tx = tDao.dohvatiRaspon(c.odMillis(), c.doMillisIskljucivo()).filter { !it.transakcija.jePrijenos && it.transakcija.tip == TipTransakcije.TROSAK }
        val poKat = tx.groupBy { it.kategorija }.mapValues { (_, l) -> l.sumOf { it.transakcija.iznosCenti } }
        poKat.forEach { (k, potroseno) ->
            val limit = k.mjesecniLimitCenti?.takeIf { it > 0 } ?: return@forEach
            val kljuc = "${c.sidro}_${k.id}"
            if (prognozaPrekoracenja(potroseno, limit, proteklo, c.brojDana) && !postavke.prognozaPoslana(kljuc)) {
                Obavijesti.prognoza(context, k.id, k.naziv, prognoza(potroseno, proteklo, c.brojDana), limit)
                postavke.oznaciPrognozu(kljuc)
            }
        }
    }

    /** Zadnjih 7 dana i prethodnih 7 (za tjedni pregled). */
    suspend fun tjedniSazetak(danas: LocalDate = LocalDate.now()): TjedniSazetak {
        val zona = ZoneId.systemDefault()
        val tx = tDao.dohvatiRaspon(
            danas.minusDays(13).atStartOfDay(zona).toInstant().toEpochMilli(),
            danas.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli(),
        )
        return tjedniSazetak(tx, danas)
    }

    /** Ima li danas ijedan unos (za dnevni podsjetnik). */
    suspend fun imaUnosaDanas(): Boolean {
        val zona = ZoneId.systemDefault()
        val d = LocalDate.now()
        return tDao.dohvatiRaspon(d.atStartOfDay(zona).toInstant().toEpochMilli(), d.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli())
            .any { !it.transakcija.jePrijenos && it.transakcija.ponavljajuciId == null }
    }

    private suspend fun promjena() {
        StanjeWidget.osvjezi(context)
        runCatching { provjeriPrognoze() }
        Obavijesti.provjeriMinus(context, tDao.ukupnoStanje())
        BackupManager.get(context).nakonPromjene()
    }

    /**
     * Stvara transakcije za sve dospjele ponavljajuće unose (i propuštene mjesece).
     * Poziva se pri otvaranju aplikacije, s widgeta i periodično iz pozadine. Vraća broj stvorenih unosa.
     */
    suspend fun generirajPonavljajuce(danas: LocalDate = LocalDate.now(), obavijesti: Boolean = true): Int = generiranje.withLock {
        val zona = ZoneId.systemDefault()
        var ukupno = 0
        val naplaceno = mutableListOf<Pair<String, Long>>()
        db.withTransaction {
            rDao.dohvatiAktivne().forEach { p ->
                val datumi = Ponavljanje.dospjeliDatumi(p, danas)
                if (datumi.isEmpty()) return@forEach
                datumi.forEach { d ->
                    tDao.spremi(
                        Transakcija(
                            tip = p.tip,
                            iznosCenti = p.iznosCenti,
                            datumVrijeme = d.atStartOfDay(zona).plusMinutes(p.vrijemeMinuta.toLong()).toInstant().toEpochMilli(),
                            kategorijaId = p.kategorijaId,
                            opis = p.opis,
                            nacinPlacanja = p.nacinPlacanja,
                            ponavljajuciId = p.id,
                        ),
                    )
                }
                rDao.spremi(p.copy(zadnjiEpochDan = datumi.last().toEpochDay()))
                ukupno += datumi.size
                val naziv = p.opis.ifBlank { kDao.dohvati(p.kategorijaId)?.naziv ?: "Ponavljajući unos" }
                repeat(datumi.size) { naplaceno += naziv to (if (p.tip == TipTransakcije.PRIHOD) p.iznosCenti else -p.iznosCenti) }
            }
        }
        if (ukupno > 0) promjena()
        if (obavijesti) Obavijesti.ponavljajuci(context, naplaceno)
        ukupno
    }

    /** Podaci za widget: stanje računa te ulaz i izlaz u aktivnom ciklusu. */
    suspend fun sazetak(): Sazetak {
        val c = ciklusZa(LocalDate.now())
        return Sazetak(tDao.ukupnoStanje(), tDao.dohvatiRaspon(c.odMillis(), c.doMillisIskljucivo()).zbroj(), c)
    }

    data class Sazetak(val stanje: Long, val ciklus: Zbroj, val raspon: Ciklus)

    companion object {
        /** Oznaka (ikonaNaziv) skrivene kategorije prijenosa. */
        const val KATEGORIJA_PRIJENOSA = "__prijenos"

        fun prilagodbeMapa(lista: List<PrilagodbaCiklusa>): Map<YearMonth, LocalDate> =
            lista.mapNotNull { p -> runCatching { YearMonth.parse(p.sidro) to LocalDate.ofEpochDay(p.pocetakEpochDan) }.getOrNull() }.toMap()

        @Volatile private var instance: BudgetRepository? = null
        fun get(context: Context): BudgetRepository = instance ?: synchronized(this) {
            instance ?: BudgetRepository(context.applicationContext).also { instance = it }
        }
    }
}
