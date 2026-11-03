package hr.smartbudget.app

import hr.smartbudget.app.domain.CycleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PrilagodbaCiklusaTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val lis = YearMonth.of(2026, 10)
    private val stu = YearMonth.of(2026, 11)

    @Test fun primjerKorisnika9Do9() {
        // Zadani dan 10.; ovaj ciklus ručno od 9. 10. do 9. 11. → početak listopadskog 9. 10., studenog ostaje 10. 11.
        val m = CycleManager(10, prilagodbe = mapOf(lis to d("2026-10-09")))
        val c = m.ciklusZa(d("2026-11-03"))
        assertEquals(d("2026-10-09"), c.pocetak)
        assertEquals(d("2026-11-09"), c.kraj)
        // Prethodni ciklus sada završava 8. 10.
        val p = m.ciklusZa(d("2026-10-08"))
        assertEquals(d("2026-09-10"), p.pocetak)
        assertEquals(d("2026-10-08"), p.kraj)
        assertTrue(m.jePrilagoden(lis))
    }

    @Test fun pomakKrajaPomičeSljedeci() {
        // Ciklus 10. 10. – 14. 11.: sljedeći počinje 15. 11.
        val m = CycleManager(10, prilagodbe = mapOf(stu to d("2026-11-15")))
        assertEquals(d("2026-11-14"), m.ciklusZa(d("2026-11-12")).kraj)
        val s = m.ciklusZa(d("2026-11-15"))
        assertEquals(d("2026-11-15"), s.pocetak)
        assertEquals(d("2026-12-09"), s.kraj)
    }

    @Test fun pocetakUPrethodnomMjesecu() {
        // Listopadski ciklus počinje već 28. 9.
        val m = CycleManager(10, prilagodbe = mapOf(lis to d("2026-09-28")))
        val c = m.ciklusZa(d("2026-09-29"))
        assertEquals(lis, c.sidro)
        assertEquals(d("2026-09-28"), c.pocetak)
        assertEquals(d("2026-09-27"), m.ciklusZa(d("2026-09-27")).kraj)
    }

    @Test fun provjeraRaspona() {
        val m = CycleManager(10)
        assertNull(m.provjeriRaspon(lis, d("2026-10-09"), d("2026-11-09")))
        assertNotNull(m.provjeriRaspon(lis, d("2026-11-09"), d("2026-10-09")))
        assertNotNull(m.provjeriRaspon(lis, d("2026-09-10"), d("2026-11-09"))) // proguta prethodni
        assertNotNull(m.provjeriRaspon(lis, d("2026-10-10"), d("2026-12-09"))) // proguta sljedeći
    }

    @Test fun bezPrilagodbiIstoKaoPrije() {
        val m = CycleManager(15)
        var x = d("2026-01-01")
        while (x.year == 2026) {
            val c = m.ciklusZa(x)
            assertTrue(x in c)
            x = x.plusDays(1)
        }
    }
}
