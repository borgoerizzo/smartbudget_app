package hr.smartbudget.app.domain

import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.data.Rezervacija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import java.time.LocalDate
import java.time.ZoneId

/** Stanje tekućeg računa s rezervacijama. */
data class StanjeTekuceg(
    val racun: Racun,
    /** Stvarno stanje računa sada. */
    val stanje: Long,
    /** Zbroj aktivnih rezervacija. */
    val rezervirano: Long,
    val rezervacije: List<Rezervacija>,
) {
    /** Korisnik je unio stvarno stanje (inače se računa iz početnog stanja i unosa). */
    val uneseno get() = racun.sidroCenti != null && racun.sidroMillis != null
    val aktivne get() = rezervacije.filter { it.aktivna }
    /** Koliko se smije potrošiti bez diranja rezerviranog. */
    val slobodno get() = stanje - rezervirano
    /** Stanje je palo ispod rezerviranog iznosa: zaslon postaje crven dok se to ne popravi. */
    val zadireURezervu get() = rezervirano > 0 && stanje < rezervirano
    /** Koliko je rezerviranog novca potrošeno (0 kad nije). */
    val manjak get() = (rezervirano - stanje).coerceAtLeast(0)
}

/** Stanje tekućeg računa na kraju dana i tada rezervirani iznos. */
data class TockaRacuna(val datum: LocalDate, val stanje: Long, val rezervirano: Long)

object TekuciRacun {
    private fun Transakcija.predznak() = if (tip == TipTransakcije.PRIHOD) iznosCenti else -iznosCenti

    /**
     * Stanje računa u trenutku [doMillis] (uključivo).
     * S unesenim stanjem (sidro): sidro + unosi nakon trenutka unosa − (unosi između [doMillis] i trenutka unosa).
     * Unosi s datumom prije trenutka unosa (npr. naknadno upisan jučerašnji trošak) ne mijenjaju današnje stanje,
     * jer ih uneseno stvarno stanje s banke već sadrži.
     */
    fun stanje(r: Racun, tx: List<Transakcija>, doMillis: Long = Long.MAX_VALUE): Long {
        val moje = tx.filter { it.nacinPlacanja == r.naziv }
        val doTrenutka = moje.filter { it.datumVrijeme <= doMillis }.sumOf { it.predznak() }
        val sidro = r.sidroCenti
        val kada = r.sidroMillis
        return if (sidro != null && kada != null) sidro + doTrenutka - moje.filter { it.datumVrijeme <= kada }.sumOf { it.predznak() }
        else r.pocetnoStanjeCenti + doTrenutka
    }

    /** Zbroj rezervacija aktivnih u trenutku [uMillis]. */
    fun rezervirano(rez: List<Rezervacija>, uMillis: Long = Long.MAX_VALUE): Long =
        rez.filter { it.kreiranMillis <= uMillis && (it.oslobodenoMillis == null || it.oslobodenoMillis > uMillis) }.sumOf { it.iznosCenti }

    fun stanjeTekuceg(racuni: List<Racun>, tx: List<Transakcija>, rez: List<Rezervacija>): StanjeTekuceg? {
        val r = racuni.firstOrNull { it.tekuci } ?: return null
        return StanjeTekuceg(r, stanje(r, tx), rezervirano(rez), rez)
    }

    /** Stanje na kraju svakog dana od [od] do [doDana] (uključivo). */
    fun poDanima(r: Racun, tx: List<Transakcija>, rez: List<Rezervacija>, od: LocalDate, doDana: LocalDate, zona: ZoneId = ZoneId.systemDefault()): List<TockaRacuna> {
        if (doDana.isBefore(od)) return emptyList()
        val moje = tx.filter { it.nacinPlacanja == r.naziv }.sortedBy { it.datumVrijeme }
        fun krajDana(d: LocalDate) = d.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli() - 1
        // Stanje do početka razdoblja jednim izračunom, zatim kumulativno po danima.
        var stanje = stanje(r, moje, krajDana(od.minusDays(1)))
        var i = moje.indexOfFirst { it.datumVrijeme > krajDana(od.minusDays(1)) }.let { if (it < 0) moje.size else it }
        val tocke = ArrayList<TockaRacuna>()
        var d = od
        while (!d.isAfter(doDana)) {
            val kraj = krajDana(d)
            while (i < moje.size && moje[i].datumVrijeme <= kraj) { stanje += moje[i].predznak(); i++ }
            tocke += TockaRacuna(d, stanje, rezervirano(rez, kraj))
            d = d.plusDays(1)
        }
        return tocke
    }
}
