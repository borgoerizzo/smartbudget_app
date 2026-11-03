package hr.smartbudget.app.data

import android.content.Context
import hr.smartbudget.app.domain.Ciklus
import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.PRAG_UPOZORENJA
import hr.smartbudget.app.domain.Ponavljanje
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

    suspend fun ciklusZa(datum: LocalDate): Ciklus =
        postavke().let { CycleManager(it.pocetniDanUCiklusu, it.nacinObradeKrajaMjeseca).ciklusZa(datum) }

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

    suspend fun obrisiTransakciju(t: Transakcija) {
        tDao.obrisi(t)
        promjena()
    }

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
        val novih = generirajPonavljajuce()
        if (novih == 0) promjena()
        return novih
    }

    suspend fun obrisiPonavljajuci(p: PonavljajuciUnos) {
        rDao.obrisi(p)
        promjena()
    }

    /** Nakon svake promjene podataka: osvježi widget i (ako je uključeno) zakaži sigurnosnu kopiju. */
    private suspend fun promjena() {
        StanjeWidget.osvjezi(context)
        BackupManager.get(context).nakonPromjene()
    }

    /**
     * Stvara transakcije za sve dospjele ponavljajuće unose (i propuštene mjesece).
     * Poziva se pri otvaranju aplikacije, s widgeta i periodično iz pozadine. Vraća broj stvorenih unosa.
     */
    suspend fun generirajPonavljajuce(danas: LocalDate = LocalDate.now()): Int = generiranje.withLock {
        val zona = ZoneId.systemDefault()
        var ukupno = 0
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
            }
        }
        if (ukupno > 0) promjena()
        ukupno
    }

    /** Podaci za widget: stanje računa te ulaz i izlaz u aktivnom ciklusu. */
    suspend fun sazetak(): Sazetak {
        val c = ciklusZa(LocalDate.now())
        return Sazetak(tDao.ukupnoStanje(), tDao.dohvatiRaspon(c.odMillis(), c.doMillisIskljucivo()).zbroj(), c)
    }

    data class Sazetak(val stanje: Long, val ciklus: Zbroj, val raspon: Ciklus)

    companion object {
        @Volatile private var instance: BudgetRepository? = null
        fun get(context: Context): BudgetRepository = instance ?: synchronized(this) {
            instance ?: BudgetRepository(context.applicationContext).also { instance = it }
        }
    }
}
