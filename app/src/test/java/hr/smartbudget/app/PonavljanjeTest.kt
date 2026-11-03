package hr.smartbudget.app

import hr.smartbudget.app.data.PonavljajuciUnos
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.domain.Ponavljanje
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PonavljanjeTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun p(dan: Int, pocetak: String, zadnji: String? = null, aktivan: Boolean = true) = PonavljajuciUnos(
        tip = TipTransakcije.TROSAK, iznosCenti = 1000, kategorijaId = 1, danUMjesecu = dan,
        pocetakEpochDan = d(pocetak).toEpochDay(), zadnjiEpochDan = zadnji?.let { d(it).toEpochDay() }, aktivan = aktivan,
    )

    @Test fun prviUnosNaDanPocetka() {
        assertEquals(listOf(d("2026-11-03")), Ponavljanje.dospjeliDatumi(p(3, "2026-11-03"), d("2026-11-03")))
    }

    @Test fun buduciPocetakNistaNeStvara() {
        assertEquals(emptyList<LocalDate>(), Ponavljanje.dospjeliDatumi(p(10, "2026-11-10"), d("2026-11-03")))
        assertEquals(d("2026-11-10"), Ponavljanje.sljedeciDatum(p(10, "2026-11-10")))
    }

    @Test fun nadoknadjujePropusteneMjesece() {
        val r = Ponavljanje.dospjeliDatumi(p(15, "2026-08-15", zadnji = "2026-08-15"), d("2026-11-20"))
        assertEquals(listOf(d("2026-09-15"), d("2026-10-15"), d("2026-11-15")), r)
    }

    @Test fun dan31UKracimMjesecima() {
        val r = Ponavljanje.dospjeliDatumi(p(31, "2027-01-31"), d("2027-05-01"))
        assertEquals(listOf(d("2027-01-31"), d("2027-02-28"), d("2027-03-31"), d("2027-04-30")), r)
        assertEquals(d("2028-02-29"), Ponavljanje.datumUMjesecu(31, java.time.YearMonth.of(2028, 2)))
    }

    @Test fun nakonZadnjegSljedeciMjesec() {
        assertEquals(d("2026-12-10"), Ponavljanje.sljedeciDatum(p(10, "2026-10-10", zadnji = "2026-11-10")))
        assertEquals(emptyList<LocalDate>(), Ponavljanje.dospjeliDatumi(p(10, "2026-10-10", zadnji = "2026-11-10"), d("2026-12-09")))
    }

    @Test fun pauziraniNeStvara() {
        assertEquals(emptyList<LocalDate>(), Ponavljanje.dospjeliDatumi(p(1, "2026-01-01", aktivan = false), d("2026-11-03")))
    }

    @Test fun pocetakUSrediniMjesecaPreskaceRanijiDan() {
        // Pravilo za 5. u mjesecu postavljeno 20. 10.: prvi unos tek 5. 11.
        assertEquals(listOf(d("2026-11-05")), Ponavljanje.dospjeliDatumi(p(5, "2026-10-20"), d("2026-11-10")))
    }
}
