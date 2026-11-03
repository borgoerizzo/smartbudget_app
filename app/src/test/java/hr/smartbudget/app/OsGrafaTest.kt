package hr.smartbudget.app

import hr.smartbudget.app.domain.Os
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsGrafaTest {
    @Test fun maliMinusJeVidljiv() {
        // Stanje −75 €, prihodi 2.400 €: minus mora zauzeti barem ~15 % visine.
        val os = Os.izracunaj(-7_500.0, 240_000.0)
        val udioMinusa = -os.min / (os.max - os.min)
        assertTrue("udio minusa $udioMinusa", udioMinusa >= 0.14)
        assertTrue(os.min <= -7_500.0)
        assertTrue(0.0 in os.oznake)
    }

    @Test fun velikiMinusPratiPodatke() {
        val os = Os.izracunaj(-120_000.0, 240_000.0)
        assertTrue(os.min <= -120_000.0)
        assertTrue(os.min >= -200_000.0) // ne pretjerano
    }

    @Test fun bezMinusaDnoJeNula() {
        val os = Os.izracunaj(0.0, 250_000.0)
        assertEquals(0.0, os.min, 0.0)
        assertTrue(os.max >= 250_000.0)
    }
}
