package hr.smartbudget.app

import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.UvozCsv
import hr.smartbudget.app.domain.prijedloziZaNaziv
import hr.smartbudget.app.domain.prognoza
import hr.smartbudget.app.domain.prognozaPrekoracenja
import hr.smartbudget.app.domain.zbroj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DodatnoTest {
    private val kat = Kategorija(1, "Namirnice", "#FFFFFF", "cart", TipTransakcije.TROSAK)
    private fun t(opis: String, centi: Long, tip: TipTransakcije = TipTransakcije.TROSAK, prijenos: Boolean = false) =
        TransakcijaSKategorijom(Transakcija(tip = tip, iznosCenti = centi, datumVrijeme = 0, kategorijaId = 1, opis = opis, jePrijenos = prijenos), kat)

    @Test fun prijenosNijePrihodNiTrosak() {
        val z = listOf(t("Plaća", 200_000, TipTransakcije.PRIHOD), t("Spar", 5_000), t("P", 10_000, prijenos = true), t("P", 10_000, TipTransakcije.PRIHOD, prijenos = true)).zbroj()
        assertEquals(200_000, z.prihodi)
        assertEquals(5_000, z.troskovi)
    }

    @Test fun prognozaLimita() {
        assertEquals(30_000, prognoza(10_000, 10, 30))
        assertTrue(prognozaPrekoracenja(10_000, 25_000, 10, 30))
        assertFalse(prognozaPrekoracenja(10_000, 40_000, 10, 30))
        assertFalse(prognozaPrekoracenja(26_000, 25_000, 10, 30)) // već prekoračeno: javlja drugo upozorenje
        assertFalse(prognozaPrekoracenja(5_000, 6_000, 1, 30)) // prerano za procjenu
    }

    @Test fun prijedlozi() {
        val zadnje = listOf(t("Spar Zagreb", 950), t("Spar Zagreb", 1300), t("Lidl", 5000), t("Spin", 1300))
        val p = prijedloziZaNaziv(zadnje, "sp", TipTransakcije.TROSAK)
        assertEquals(listOf("Spar Zagreb", "Spin"), p.map { it.transakcija.opis })
        assertTrue(prijedloziZaNaziv(zadnje, "", TipTransakcije.TROSAK).isEmpty())
    }

    @Test fun csvSaSeparatoromINavodnicima() {
        val csv = "﻿Datum;Opis;Iznos\r\n15.10.2026.;\"Konzum; Zagreb\";-53,00\r\n16.10.2026.;Plaća;2.400,00\r\n"
        val tab = UvozCsv.parsiraj(csv)
        assertEquals(listOf("Datum", "Opis", "Iznos"), tab.zaglavlje)
        assertEquals(2, tab.redovi.size)
        assertEquals("Konzum; Zagreb", tab.redovi[0][1])
        assertEquals(Triple(0, 2, 1), UvozCsv.pogodiStupce(tab.zaglavlje))
        assertEquals(-5_300L, UvozCsv.iznos(tab.redovi[0][2]))
        assertEquals(240_000L, UvozCsv.iznos(tab.redovi[1][2]))
    }

    @Test fun csvZarez() {
        val tab = UvozCsv.parsiraj("date,description,amount\n2026-10-15,Coffee,-2.40\n")
        assertEquals(Triple(0, 2, 1), UvozCsv.pogodiStupce(tab.zaglavlje))
        assertEquals(-240L, UvozCsv.iznos(tab.redovi[0][2]))
    }

    @Test fun datumi() {
        val d = LocalDate.of(2026, 10, 15)
        listOf("15.10.2026.", "15.10.2026", "2026-10-15", "15/10/2026", "15.10.2026 08:30", "2026-10-15T08:30").forEach {
            assertEquals(it, d, UvozCsv.datum(it))
        }
        assertNull(UvozCsv.datum("nije datum"))
    }
}
