package hr.smartbudget.app

import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.Zbroj
import hr.smartbudget.app.domain.fiksniPromjenjivi
import hr.smartbudget.app.domain.kalendarPotrosnje
import hr.smartbudget.app.domain.najcesciNazivi
import hr.smartbudget.app.domain.prosjekPoDanuUTjednu
import hr.smartbudget.app.domain.stopaStednje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NavikeTest {
    private val kat = Kategorija(1, "Namirnice", "#FFFFFF", "cart", TipTransakcije.TROSAK)
    private fun t(d: LocalDate, centi: Long, opis: String = "", ponavljajuci: Long? = null, tip: TipTransakcije = TipTransakcije.TROSAK, prijenos: Boolean = false) =
        TransakcijaSKategorijom(
            Transakcija(
                tip = tip, iznosCenti = centi, kategorijaId = 1, opis = opis, ponavljajuciId = ponavljajuci, jePrijenos = prijenos,
                datumVrijeme = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
            kat,
        )

    @Test fun kalendarImaSveDaneCiklusa() {
        val c = CycleManager(10).ciklusZa(LocalDate.of(2026, 11, 3))
        val k = kalendarPotrosnje(c, listOf(t(LocalDate.of(2026, 10, 10), 500), t(LocalDate.of(2026, 10, 10), 250), t(LocalDate.of(2026, 10, 11), 100, prijenos = true)))
        assertEquals(31, k.size)
        assertEquals(750L, k.first().second)
        assertEquals(0L, k[1].second) // prijenos se ne broji
    }

    @Test fun prosjekPoDanuDijeliSBrojemTihDana() {
        // 2 ponedjeljka u razdoblju, trošak samo u jednom: prosjek = pola
        val od = LocalDate.of(2026, 11, 2) // ponedjeljak
        val doD = LocalDate.of(2026, 11, 15)
        val p = prosjekPoDanuUTjednu(listOf(t(od, 2_000), t(LocalDate.of(2026, 11, 7), 600)), od, doD)
        assertEquals(1_000L, p[0])
        assertEquals(300L, p[5]) // subota
        assertEquals(0L, p[1])
    }

    @Test fun fiksniIPromjenjivi() {
        val d = LocalDate.of(2026, 11, 2)
        val f = fiksniPromjenjivi(listOf(t(d, 48_000, ponavljajuci = 1), t(d, 1_000, ponavljajuci = 2), t(d, 5_000), t(d, 200_000, tip = TipTransakcije.PRIHOD)))
        assertEquals(49_000L, f.fiksni)
        assertEquals(5_000L, f.promjenjivi)
        assertEquals(48_000L, f.najveciFiksni.first().transakcija.iznosCenti)
    }

    @Test fun stopa() {
        assertEquals(25.0, stopaStednje(Zbroj(200_000, 150_000))!!, 0.001)
        assertEquals(-10.0, stopaStednje(Zbroj(100_000, 110_000))!!, 0.001)
        assertNull(stopaStednje(Zbroj(0, 5_000)))
    }

    @Test fun najcesciNazivi() {
        val d = LocalDate.of(2026, 11, 2)
        val n = najcesciNazivi(listOf(t(d, 950, "Spar"), t(d, 1300, "SPAR"), t(d, 750, "Spar"), t(d, 5000, "Lidl"), t(d, 400, "Lidl"), t(d, 9999, "Jednom")))
        assertEquals(listOf("Spar", "Lidl"), n.map { it.naziv })
        assertEquals(3, n[0].broj)
        assertEquals(3000L, n[0].ukupno)
        assertEquals(1000L, n[0].prosjek)
    }
}
