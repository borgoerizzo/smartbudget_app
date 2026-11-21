package hr.smartbudget.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Podsjetnici na rok ulaganja: 30, 15, 5 i 1 dan prije te na dan isteka, da korisnik na vrijeme
 * odluči hoće li reinvestirati ili podići novac.
 */
object Rokovi {
    val PRAGOVI = listOf(30, 15, 5, 1, 0)

    /** [kljuc] razlikuje ulaganja ("p12" = pozicija, "n3" = novčanik). */
    data class Rok(val kljuc: String, val naziv: String, val datum: LocalDate)

    /** Podsjetnik za slanje: koliko je dana do roka i koje pragove označiti kao poslane. */
    data class Podsjetnik(val rok: Rok, val dana: Int, val kljucevi: List<String>)

    fun kljuc(r: Rok, prag: Int) = "${r.kljuc}@${r.datum}@$prag"

    /**
     * Rokovi za koje je danas red za podsjetnik. Ako je neki prag preskočen (npr. mobitel je bio isključen),
     * šalje se jedan podsjetnik sa stvarnim brojem dana i označe se svi prijeđeni pragovi, pa se ništa ne ponavlja.
     * Promjena datuma roka daje nove ključeve, pa podsjetnici kreću iznova.
     */
    fun zaDanas(rokovi: List<Rok>, danas: LocalDate, poslani: Set<String>): List<Podsjetnik> = rokovi.mapNotNull { r ->
        val dana = ChronoUnit.DAYS.between(danas, r.datum).toInt()
        if (dana < 0 || dana > PRAGOVI.max()) return@mapNotNull null
        val prijedeni = PRAGOVI.filter { it >= dana }.map { kljuc(r, it) }
        if (prijedeni.all { it in poslani }) null else Podsjetnik(r, dana, prijedeni)
    }

    /** Kratki opis roka za prikaz: "rok 12.03.2027. (za 14 dana)" ili "rok istekao". */
    fun opis(datum: LocalDate, danas: LocalDate = LocalDate.now()): String {
        val dana = ChronoUnit.DAYS.between(danas, datum)
        return when {
            dana < 0 -> "rok istekao ${HrDatum.puni(datum)}"
            dana == 0L -> "rok ističe danas"
            dana == 1L -> "rok ističe sutra"
            else -> "rok ${HrDatum.puni(datum)} (za $dana dana)"
        }
    }
}
