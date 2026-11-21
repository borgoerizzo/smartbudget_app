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
class BudgetRepository private constructor(val context: Context) {
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

    /**
     * Brisanje; dio prijenosa briše i drugi dio (inače bi stanja računa bila kriva), a prijenos na štednju
     * (ili sa nje) poništava i uplatu na cilj odnosno promjenu novčanika, kao da prijenosa nije ni bilo.
     * Vraća true ako je poništena i štednja.
     */
    suspend fun obrisiTransakciju(t: Transakcija): Boolean = obrisiTransakcije(listOf(t)) > 0

    /** Brisanje više unosa odjednom (višestruki odabir). Vraća broj prijenosa kojima je poništena i štednja. */
    suspend fun obrisiTransakcije(lista: List<Transakcija>): Int {
        var stednja = 0
        db.withTransaction {
            val obrisaneGrupe = mutableSetOf<Long>()
            lista.distinctBy { it.id }.forEach { t ->
                val grupa = t.grupaId.takeIf { t.jePrijenos }
                if (grupa != null && !obrisaneGrupe.add(grupa)) return@forEach
                if (t.naStednju && ponistiStednju(t)) stednja++
                if (grupa != null) tDao.obrisiGrupu(grupa) else tDao.obrisi(t)
            }
        }
        lista.forEach { t -> t.slika?.let { runCatching { java.io.File(it).delete() } } }
        promjena()
        return stednja
    }

    /**
     * Poništava štednju koju je stvorio prijenos [t]: briše uplatu na cilj (i vraća ušteđeni iznos cilja)
     * ili promjenu novčanika. Prijenosi iz starijih verzija nemaju vezu pa se traži jedina uplata
     * ili promjena istog dana i iznosa; ako ih je više, ništa se ne dira (ne može se znati koja je).
     */
    private suspend fun ponistiStednju(t: Transakcija): Boolean {
        val uplate = db.uplate().sve()
        val promjene = db.novcanici().svePromjene()
        var vezaneUplate = t.grupaId?.let { g -> uplate.filter { it.grupaId == g } }.orEmpty()
        var vezanePromjene = t.grupaId?.let { g -> promjene.filter { it.grupaId == g } }.orEmpty()
        if (vezaneUplate.isEmpty() && vezanePromjene.isEmpty()) {
            val dan = Instant.ofEpochMilli(t.datumVrijeme).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val naStednju = t.tip == TipTransakcije.TROSAK
            val kandidatiUplata = uplate.filter {
                it.grupaId == null && it.valuta == null && it.epochDan == dan &&
                    it.iznosCenti == (if (naStednju) t.iznosCenti else -t.iznosCenti)
            }
            val napomena = if (naStednju) "s računa ${t.nacinPlacanja}" else "na račun ${t.nacinPlacanja}"
            val kandidatiPromjena = promjene.filter { it.grupaId == null && it.epochDan == dan && it.napomena == napomena }
            when {
                kandidatiUplata.size == 1 && kandidatiPromjena.isEmpty() -> vezaneUplate = kandidatiUplata
                kandidatiPromjena.size == 1 && kandidatiUplata.isEmpty() -> vezanePromjene = kandidatiPromjena
                else -> return false
            }
        }
        val ciljevi = db.ciljevi().sve().associateBy { it.id }.toMutableMap()
        vezaneUplate.forEach { u ->
            if (u.valuta == null) ciljevi[u.ciljId]?.let { c ->
                val novi = c.copy(ustedjenoCenti = (c.ustedjenoCenti - u.iznosCenti).coerceAtLeast(0))
                db.ciljevi().spremi(novi)
                ciljevi[c.id] = novi
            }
            db.uplate().obrisi(u)
        }
        vezanePromjene.forEach { db.novcanici().obrisiPromjenu(it) }
        return true
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

    /**
     * Prijenos s računa na štednju: odljev s računa (prijenos, ne broji se kao trošak) i uplata na cilj
     * ili u novčanik. [jedinice] je iznos u valuti novčanika (za cilj se ne koristi).
     */
    suspend fun prijenosNaStednju(
        s: String, iznosCenti: Long, kada: Long, opis: String,
        cilj: CiljStednje?, novcanik: Novcanik?, jedinice: Long,
    ) {
        val kat = kategorijaPrijenosa()
        val odrediste = cilj?.naziv ?: novcanik?.naziv ?: ""
        val naziv = opis.ifBlank { "Prijenos na štednju: $odrediste" }
        val dan = java.time.Instant.ofEpochMilli(kada).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val grupa = System.currentTimeMillis()
        db.withTransaction {
            tDao.spremi(Transakcija(tip = TipTransakcije.TROSAK, iznosCenti = iznosCenti, datumVrijeme = kada, kategorijaId = kat, opis = naziv, nacinPlacanja = s, jePrijenos = true, grupaId = grupa, naStednju = true))
            if (cilj != null) {
                db.ciljevi().sve().firstOrNull { it.id == cilj.id }?.let { c ->
                    db.ciljevi().spremi(c.copy(ustedjenoCenti = c.ustedjenoCenti + iznosCenti))
                    db.uplate().umetni(UplataCilja(ciljId = c.id, iznosCenti = iznosCenti, epochDan = dan, grupaId = grupa))
                }
            }
            if (novcanik != null) {
                val kripto = hr.smartbudget.app.domain.Valute.po(novcanik.valuta).kripto
                db.novcanici().umetniPromjenu(
                    PromjenaNovcanika(novcanikId = novcanik.id, jedinice = jedinice, epochDan = dan, ulozenoCenti = if (kripto) iznosCenti else null, napomena = "s računa $s", grupaId = grupa),
                )
            }
        }
        promjena()
    }

    /**
     * Prijenos sa štednje natrag na račun: priljev na račun (vraća stanje ciklusa) i povlačenje s cilja
     * ili isplata iz novčanika. [jedinice] je iznos koji se skida s novčanika u njegovoj valuti.
     */
    suspend fun prijenosSaStednje(
        na: String, iznosCenti: Long, kada: Long, opis: String,
        cilj: CiljStednje?, novcanik: Novcanik?, jedinice: Long,
    ) {
        val kat = kategorijaPrijenosa()
        val izvor = cilj?.naziv ?: novcanik?.naziv ?: ""
        val naziv = opis.ifBlank { "Prijenos sa štednje: $izvor" }
        val dan = java.time.Instant.ofEpochMilli(kada).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val grupa = System.currentTimeMillis()
        db.withTransaction {
            tDao.spremi(Transakcija(tip = TipTransakcije.PRIHOD, iznosCenti = iznosCenti, datumVrijeme = kada, kategorijaId = kat, opis = naziv, nacinPlacanja = na, jePrijenos = true, grupaId = grupa, naStednju = true))
            if (cilj != null) {
                db.ciljevi().sve().firstOrNull { it.id == cilj.id }?.let { c ->
                    val novo = (c.ustedjenoCenti - iznosCenti).coerceAtLeast(0)
                    db.ciljevi().spremi(c.copy(ustedjenoCenti = novo))
                    db.uplate().umetni(UplataCilja(ciljId = c.id, iznosCenti = novo - c.ustedjenoCenti, epochDan = dan, grupaId = grupa))
                }
            }
            if (novcanik != null) {
                db.novcanici().umetniPromjenu(PromjenaNovcanika(novcanikId = novcanik.id, jedinice = -jedinice, epochDan = dan, napomena = "na račun $na", grupaId = grupa))
            }
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

    /** Sprema cilj; promjena ušteđenog iznosa bilježi se kao uplata (ili povlačenje). */
    suspend fun spremiCilj(c: CiljStednje) = db.withTransaction {
        val danas = LocalDate.now().toEpochDay()
        val stari = if (c.id == 0L) null else db.ciljevi().sve().firstOrNull { it.id == c.id }
        val novi = c.copy(kreiranEpochDan = c.kreiranEpochDan ?: stari?.kreiranEpochDan ?: danas)
        val id = db.ciljevi().spremi(novi).let { if (c.id == 0L) it else c.id }
        val razlika = novi.ustedjenoCenti - (stari?.ustedjenoCenti ?: 0L)
        if (razlika != 0L) db.uplate().umetni(UplataCilja(ciljId = id, iznosCenti = razlika, epochDan = danas))
    }.also { BackupManager.get(context).nakonPromjene() }

    /**
     * Briše pogrešno unesenu uplatu (ili povlačenje) s cilja. Uplata u osnovnoj valuti
     * mijenja i ušteđeni iznos cilja; strane valute se računaju iz samih uplata.
     */
    suspend fun obrisiUplatuCilja(u: UplataCilja) = db.withTransaction {
        if (u.valuta == null) {
            db.ciljevi().sve().firstOrNull { it.id == u.ciljId }?.let { c ->
                db.ciljevi().spremi(c.copy(ustedjenoCenti = (c.ustedjenoCenti - u.iznosCenti).coerceAtLeast(0)))
            }
        }
        db.uplate().obrisi(u)
    }.also { BackupManager.get(context).nakonPromjene() }

    /** Uplata ili povlačenje na cilj u stranoj valuti / kriptovaluti (iznos u najmanjim jedinicama te valute). */
    suspend fun uplatiNaCiljUValuti(c: CiljStednje, jedinice: Long, valuta: String) =
        db.uplate().umetni(UplataCilja(ciljId = c.id, iznosCenti = jedinice, epochDan = LocalDate.now().toEpochDay(), valuta = valuta))
            .also { BackupManager.get(context).nakonPromjene() }

    /** Uplata (pozitivna) ili povlačenje (negativno) na cilj. */
    suspend fun uplatiNaCilj(c: CiljStednje, iznosCenti: Long) = db.withTransaction {
        val trenutni = db.ciljevi().sve().firstOrNull { it.id == c.id } ?: return@withTransaction
        val novo = (trenutni.ustedjenoCenti + iznosCenti).coerceAtLeast(0)
        db.ciljevi().spremi(trenutni.copy(ustedjenoCenti = novo))
        db.uplate().umetni(UplataCilja(ciljId = c.id, iznosCenti = novo - trenutni.ustedjenoCenti, epochDan = LocalDate.now().toEpochDay()))
    }.also { BackupManager.get(context).nakonPromjene() }

    // --- Novčanici (štednja u valutama i kriptovalutama) ---

    suspend fun spremiNovcanik(n: Novcanik): Long = db.novcanici().spremi(n).let { if (n.id == 0L) it else n.id }
        .also { BackupManager.get(context).nakonPromjene() }

    suspend fun obrisiNovcanik(n: Novcanik) = db.withTransaction {
        db.novcanici().obrisiPromjeneZa(n.id)
        db.novcanici().obrisi(n)
    }.also { BackupManager.get(context).nakonPromjene() }

    suspend fun dodajPromjenu(p: PromjenaNovcanika) = db.novcanici().umetniPromjenu(p).also { BackupManager.get(context).nakonPromjene() }
    suspend fun obrisiPromjenu(p: PromjenaNovcanika) = db.novcanici().obrisiPromjenu(p).also { BackupManager.get(context).nakonPromjene() }

    /**
     * Podsjetnici na rok ulaganja i novčanika (30, 15, 5, 1 dan prije i na dan isteka). Poziva se iz
     * pozadinskog posla i pri otvaranju aplikacije; svaki prag šalje se samo jednom.
     */
    suspend fun provjeriRokove() {
        val post = AppPostavke.get(context)
        if (!post.stanje.value.obavijestRok) return
        val poz = hr.smartbudget.app.domain.Portfelj.stanja(db.pozicije().sve(), db.pozicije().svePromjene())
            .filter { it.kolicinaMikro > 0 && it.pozicija.dospijeceEpochDan != null }
        val nov = hr.smartbudget.app.domain.stanjaNovcanika(db.novcanici().sve(), db.novcanici().svePromjene())
            .filter { it.stanje > 0 && it.novcanik.rokEpochDan != null }
        val rokovi = poz.map { hr.smartbudget.app.domain.Rokovi.Rok("p${it.pozicija.id}", it.pozicija.naziv, LocalDate.ofEpochDay(it.pozicija.dospijeceEpochDan!!)) } +
            nov.map { hr.smartbudget.app.domain.Rokovi.Rok("n${it.novcanik.id}", it.novcanik.naziv, LocalDate.ofEpochDay(it.novcanik.rokEpochDan!!)) }
        val zaSlanje = hr.smartbudget.app.domain.Rokovi.zaDanas(rokovi, LocalDate.now(), post.rokoviPoslani())
        if (zaSlanje.isEmpty()) return
        Obavijesti.rok(context, zaSlanje.map { it.rok.naziv to it.dana })
        post.oznaciRokove(zaSlanje.flatMap { it.kljucevi })
    }

    // --- Ulaganja u vlasništvu (dionice, ETF-ovi, obveznice, roba) ---

    /** Novo ulaganje s prvom kupnjom; ako isti simbol već postoji, kupnja se dodaje njemu. Vraća id. */
    suspend fun dodajPoziciju(p: Pozicija, prva: PromjenaPozicije?): Long = db.withTransaction {
        // Ručno uneseni zapis je uvijek zaseban (svaki ima svoj iznos i datum dospijeća).
        val postojeca = if (p.rucno) null else db.pozicije().sve().firstOrNull { !it.rucno && it.simbol.equals(p.simbol, ignoreCase = true) }
        val id = postojeca?.id ?: db.pozicije().spremi(p)
        prva?.let { db.pozicije().umetniPromjenu(it.copy(pozicijaId = id)) }
        id
    }.also { BackupManager.get(context).nakonPromjene() }

    suspend fun spremiPoziciju(p: Pozicija) = db.pozicije().spremi(p).also { BackupManager.get(context).nakonPromjene() }

    suspend fun obrisiPoziciju(p: Pozicija) = db.withTransaction {
        db.pozicije().obrisiPromjeneZa(p.id)
        db.pozicije().obrisi(p)
    }.also { BackupManager.get(context).nakonPromjene() }

    suspend fun dodajPromjenuPozicije(p: PromjenaPozicije) = db.pozicije().umetniPromjenu(p).also { BackupManager.get(context).nakonPromjene() }
    suspend fun obrisiPromjenuPozicije(p: PromjenaPozicije) = db.pozicije().obrisiPromjenu(p).also { BackupManager.get(context).nakonPromjene() }

    suspend fun obrisiCilj(c: CiljStednje) = db.withTransaction {
        db.uplate().obrisiZaCilj(c.id)
        db.ciljevi().obrisi(c)
    }.also { BackupManager.get(context).nakonPromjene() }

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
