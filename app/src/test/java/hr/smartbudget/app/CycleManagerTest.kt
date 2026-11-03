package hr.smartbudget.app

import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.Novac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CycleManagerTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun primjerIzSpecifikacije() {
        val c = CycleManager(15).ciklusZa(d("2026-11-03"))
        assertEquals(d("2026-10-15"), c.pocetak)
        assertEquals(d("2026-11-14"), c.kraj)
        assertEquals(31, c.brojDana)
        assertEquals(20, c.proteklihDana(d("2026-11-03")))
    }

    @Test fun naDanPocetkaZapocinjeNoviCiklus() {
        val c = CycleManager(15).ciklusZa(d("2026-11-15"))
        assertEquals(d("2026-11-15"), c.pocetak)
        assertEquals(d("2026-12-14"), c.kraj)
    }

    @Test fun prviUMjesecu() {
        val c = CycleManager(1).ciklusZa(d("2026-02-17"))
        assertEquals(d("2026-02-01"), c.pocetak)
        assertEquals(d("2026-02-28"), c.kraj)
    }

    @Test fun dan31UVeljaciZadnjiDan() {
        val m = CycleManager(31, zadnjiDanAkoNema = true)
        assertEquals(d("2026-02-28"), m.pocetakZaMjesec(YearMonth.of(2026, 2)))
        assertEquals(d("2028-02-29"), m.pocetakZaMjesec(YearMonth.of(2028, 2))) // prijestupna
        assertEquals(d("2026-04-30"), m.pocetakZaMjesec(YearMonth.of(2026, 4)))
        val c = m.ciklusZa(d("2026-03-10"))
        assertEquals(d("2026-02-28"), c.pocetak)
        assertEquals(d("2026-03-30"), c.kraj)
    }

    @Test fun dan31BezZadnjegDanaPrelaziNaPrvi() {
        val m = CycleManager(31, zadnjiDanAkoNema = false)
        val c = m.ciklusZa(d("2026-03-01"))
        assertEquals(d("2026-03-01"), c.pocetak)
        assertEquals(d("2026-03-30"), c.kraj)
        val prethodni = m.ciklusZa(d("2026-02-27"))
        assertEquals(d("2026-01-31"), prethodni.pocetak)
        assertEquals(d("2026-02-28"), prethodni.kraj)
    }

    @Test fun dan30UPrijestupnojVeljaci() {
        val c = CycleManager(30).ciklusZa(d("2028-03-05"))
        assertEquals(d("2028-02-29"), c.pocetak)
        assertEquals(d("2028-03-29"), c.kraj)
    }

    @Test fun ciklusiSuUzastopniKrozCijeluGodinu() {
        for (dan in 1..31) for (zadnji in listOf(true, false)) {
            val m = CycleManager(dan, zadnji)
            var c = m.ciklusZa(d("2027-12-20"))
            repeat(30) {
                val sljedeci = m.ciklusZaSidro(c.sidro.plusMonths(1))
                assertEquals("dan=$dan zadnji=$zadnji", c.kraj.plusDays(1), sljedeci.pocetak)
                c = sljedeci
            }
            var x = d("2027-01-01")
            while (x.year == 2027) {
                val k = m.ciklusZa(x)
                assert(x in k) { "$x nije u $k (dan=$dan)" }
                x = x.plusDays(1)
            }
        }
    }

    @Test fun pomakNaPrethodniISljedeci() {
        val m = CycleManager(10)
        assertEquals(d("2026-09-10"), m.ciklus(d("2026-11-03"), -1).pocetak)
        assertEquals(d("2026-11-10"), m.ciklus(d("2026-11-03"), 1).pocetak)
        assertEquals(d("2027-01-09"), m.ciklus(d("2026-11-03"), 2).kraj)
    }
}

class NovacTest {
    @Test fun formatiranje() {
        assertEquals("1.250,00 €", Novac.eur(125000))
        assertEquals("+1.250,00 €", Novac.predznak(125000))
        assertEquals("−45,80 €", Novac.predznak(-4580))
        assertEquals("0,00 €", Novac.eur(0))
        assertEquals("1.234.567,89 €", Novac.eur(123456789))
    }

    @Test fun parsiranje() {
        assertEquals(125050L, Novac.parsiraj("1.250,50"))
        assertEquals(4580L, Novac.parsiraj("45,80"))
        assertEquals(4580L, Novac.parsiraj("45.80"))
        assertEquals(125000L, Novac.parsiraj("1.250"))
        assertEquals(1200L, Novac.parsiraj("12"))
        assertEquals(1L, Novac.parsiraj("0,005"))
        assertEquals(125050L, Novac.parsiraj("1,250.50"))
        assertNull(Novac.parsiraj("abc"))
        assertNull(Novac.parsiraj(""))
    }
}
