package hr.smartbudget.app

import hr.smartbudget.app.data.Pozicija
import hr.smartbudget.app.data.PromjenaPozicije
import hr.smartbudget.app.domain.Portfelj
import hr.smartbudget.app.domain.Rokovi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RokoviTest {
    private val danas = LocalDate.of(2026, 11, 21)
    private fun rok(dana: Long) = Rokovi.Rok("p1", "RHMF-T-722N", danas.plusDays(dana))

    @Test fun podsjetniciNaPragovima() {
        listOf(30, 15, 5, 1, 0).forEach { d ->
            val p = Rokovi.zaDanas(listOf(rok(d.toLong())), danas, emptySet()).single()
            assertEquals(d, p.dana)
        }
        // Daleko ili istekao: ništa
        assertTrue(Rokovi.zaDanas(listOf(rok(31)), danas, emptySet()).isEmpty())
        assertTrue(Rokovi.zaDanas(listOf(rok(-1)), danas, emptySet()).isEmpty())
    }

    @Test fun svakiPragSamoJednom() {
        val r = rok(15)
        val prvi = Rokovi.zaDanas(listOf(r), danas, emptySet()).single()
        // Označeni su prag 30 i 15, pa sljedeći dan (14 dana) nema novog podsjetnika
        assertEquals(setOf(Rokovi.kljuc(r, 30), Rokovi.kljuc(r, 15)), prvi.kljucevi.toSet())
        assertTrue(Rokovi.zaDanas(listOf(r), danas.plusDays(1), prvi.kljucevi.toSet()).isEmpty())
        // Na 5 dana stiže novi
        assertEquals(5, Rokovi.zaDanas(listOf(r), danas.plusDays(10), prvi.kljucevi.toSet()).single().dana)
    }

    @Test fun preskoceniPragDajeJedanPodsjetnik() {
        // Mobitel je bio isključen: s 20 dana skače na 3 dana do roka → jedan podsjetnik ("za 3 dana")
        val r = rok(3)
        val p = Rokovi.zaDanas(listOf(r), danas, setOf(Rokovi.kljuc(r, 30))).single()
        assertEquals(3, p.dana)
        assertTrue(Rokovi.kljuc(r, 5) in p.kljucevi)
        assertFalse(Rokovi.kljuc(r, 1) in p.kljucevi)
    }

    @Test fun noviDatumRokaKreceIznova() {
        val star = rok(5)
        val poslano = Rokovi.zaDanas(listOf(star), danas, emptySet()).single().kljucevi.toSet()
        val nov = Rokovi.Rok("p1", "RHMF-T-722N", danas.plusDays(5).plusYears(1).minusDays(365 - 4))
        assertEquals(1, Rokovi.zaDanas(listOf(nov), danas, poslano).size)
    }

    @Test fun vrijednostIPrinosTrezorskogZapisa() {
        // 9.700 € uloženo, 10.000 € pri dospijeću za 364 dana
        val kupljeno = danas.toEpochDay()
        val dosp = danas.plusDays(364).toEpochDay()
        assertEquals(0.0, Portfelj.vrijednostZapisa(9_700.0, 10_000.0, kupljeno, dosp, kupljeno - 1), 1e-9)
        assertEquals(9_700.0, Portfelj.vrijednostZapisa(9_700.0, 10_000.0, kupljeno, dosp, kupljeno), 1e-9)
        assertEquals(9_850.0, Portfelj.vrijednostZapisa(9_700.0, 10_000.0, kupljeno, dosp, kupljeno + 182), 1e-9)
        assertEquals(10_000.0, Portfelj.vrijednostZapisa(9_700.0, 10_000.0, kupljeno, dosp, dosp + 10), 1e-9)
        val p = Portfelj.prinosGodisnji(9_700.0, 10_000.0, kupljeno, dosp)!!
        assertEquals(3.10, p, 0.01) // (10000/9700 − 1) × 365/364 ≈ 3,10 %
    }

    @Test fun rucniZapisUStanjuIGrafu() {
        val zapis = Pozicija(id = 7, simbol = "RHMF-T-722N", naziv = "Trezorski zapis RH", vrsta = "OBVEZNICE", valuta = "EUR",
            nominalCenti = 1_000_000, dospijeceEpochDan = danas.plusDays(100).toEpochDay())
        assertTrue(zapis.rucno)
        assertFalse(zapis.copy(nominalCenti = null).rucno) // samo rok, tržišno ulaganje
        val s = Portfelj.stanja(listOf(zapis), listOf(PromjenaPozicije(pozicijaId = 7, kolicinaMikro = 1_000_000, epochDan = danas.toEpochDay(), ulozenoCenti = 990_000))).single()
        assertEquals(9_900.0, s.vrijednostRucno(danas.toEpochDay())!!, 1e-9)
        assertEquals(10_000.0, s.vrijednostRucno(danas.plusDays(100).toEpochDay())!!, 1e-9)
        // Graf: ručni zapis ulazi i bez tržišnih cijena
        val v = Portfelj.vrijednostPoDanima(listOf(s), emptyMap(), { 1.0 }, danas, danas.plusDays(1))
        assertEquals(listOf(9_900.0, 9_901.0), v.map { it.second })
    }
}
