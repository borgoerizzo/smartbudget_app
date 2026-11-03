package hr.smartbudget.app.domain

import hr.smartbudget.app.data.TransakcijaSKategorijom
import java.text.Normalizer

/** Pretraga transakcija: bez razlike velikih/malih slova i dijakritika (č, ć, ž, š, đ). */
object Pretraga {
    private val bezKvacica = Regex("\\p{Mn}+")

    fun normaliziraj(s: String): String =
        Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(bezKvacica, "").trim()

    /**
     * Odgovara li transakcija upitu. Svaka riječ upita mora se naći u nazivu, kategoriji,
     * načinu plaćanja ili iznosu ("45,80", "45.80", "1250" ili "1.250,00").
     */
    fun odgovara(t: TransakcijaSKategorijom, upit: String): Boolean {
        val rijeci = normaliziraj(upit).split(' ').filter { it.isNotBlank() }
        if (rijeci.isEmpty()) return true
        val iznos = Novac.broj(t.transakcija.iznosCenti)
        val tekst = normaliziraj(
            listOf(t.transakcija.opis, t.kategorija.naziv, t.transakcija.nacinPlacanja, iznos, iznos.replace(".", ""))
                .joinToString(" "),
        )
        return rijeci.all { r -> r in tekst || r.replace('.', ',') in tekst }
    }
}
