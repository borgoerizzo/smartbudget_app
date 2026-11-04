package hr.smartbudget.app

import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.domain.StatusCilja
import hr.smartbudget.app.domain.analizirajCilj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AnalizaCiljaTest {
    private val danas = LocalDate.of(2026, 11, 4)
    private fun cilj(ustedjeno: Long, rok: LocalDate?, kreiran: LocalDate = danas) =
        CiljStednje(1, "Putovanje", 400_000, ustedjeno, rok?.toEpochDay(), "#00F5A0", "plane", kreiran.toEpochDay())

    @Test fun odbrojavanjeIPotrebniIznosi() {
        val a = analizirajCilj(cilj(0, danas.plusDays(100)), danas)
        assertEquals(100L, a.danaDoRoka)
        assertEquals(4_000L, a.dnevno)       // 40 € dnevno
        assertEquals(28_000L, a.tjedno)      // 280 € tjedno
        assertEquals(121_760L, a.mjesecno)   // ~1.217,60 € mjesečno
        assertEquals(25, a.sljedeciPrag)
        assertEquals(100_000L, a.doPraga)
    }

    @Test fun usporedbaSPlanom() {
        val kreiran = danas.minusDays(50)
        val rok = danas.plusDays(50)  // pola vremena: po planu 2.000 €
        assertEquals(StatusCilja.KASNI, analizirajCilj(cilj(100_000, rok, kreiran), danas).status)
        assertEquals(StatusCilja.NA_PUTU, analizirajCilj(cilj(200_000, rok, kreiran), danas).status)
        assertEquals(StatusCilja.ISPRED_PLANA, analizirajCilj(cilj(260_000, rok, kreiran), danas).status)
        assertEquals(200_000L, analizirajCilj(cilj(0, rok, kreiran), danas).ocekivanoDoSad)
    }

    @Test fun tempoIProcjena() {
        // 50 dana, 1.000 € → 20 €/dan, ostaje 3.000 € → 150 dana
        val a = analizirajCilj(cilj(100_000, null, danas.minusDays(50)), danas)
        assertEquals(StatusCilja.BEZ_ROKA, a.status)
        assertEquals(danas.plusDays(150), a.procjena)
        assertEquals(60_880L, a.tempoMjesecno)
        assertNull(a.dnevno)
    }

    @Test fun dosegnutIIstekao() {
        assertEquals(StatusCilja.DOSEGNUT, analizirajCilj(cilj(400_000, danas.plusDays(5)), danas).status)
        assertEquals(StatusCilja.ISTEKAO, analizirajCilj(cilj(10_000, danas.minusDays(1), danas.minusDays(30)), danas).status)
        assertNull(analizirajCilj(cilj(10_000, danas.plusDays(30)), danas).tempoMjesecno) // prekratko za tempo
    }
}
