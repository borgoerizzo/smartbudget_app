package hr.smartbudget.app

import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.tjedniSazetak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TjedniTest {
    private val danas = LocalDate.of(2026, 11, 8)
    private val kat = Kategorija(1, "Namirnice", "#FFFFFF", "cart", TipTransakcije.TROSAK)
    private fun t(d: LocalDate, centi: Long, tip: TipTransakcije = TipTransakcije.TROSAK, prijenos: Boolean = false) = TransakcijaSKategorijom(
        Transakcija(
            tip = tip, iznosCenti = centi, kategorijaId = 1, jePrijenos = prijenos,
            datumVrijeme = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        ),
        kat,
    )

    @Test fun zadnjih7DanaIUsporedba() {
        val sve = listOf(
            t(danas, 1_000), t(danas.minusDays(6), 2_000), t(danas.minusDays(7), 9_999), // 7. dan unatrag je prethodni tjedan
            t(danas.minusDays(3), 50_000, TipTransakcije.PRIHOD),
            t(danas.minusDays(2), 7_000, prijenos = true),
            t(danas.minusDays(10), 1_000),
        )
        val s = tjedniSazetak(sve, danas)
        assertEquals(danas.minusDays(6), s.od)
        assertEquals(3_000, s.zbroj.troskovi)
        assertEquals(50_000, s.zbroj.prihodi)
        assertEquals(10_999, s.prethodni.troskovi)
        assertEquals(7, s.poDanima.size)
        assertEquals(1_000, s.poDanima.last().troskovi)
        assertEquals(2_000L, s.najveciTrosak?.transakcija?.iznosCenti)
        assertEquals(3, s.brojUnosa) // prijenos se ne broji
    }
}
