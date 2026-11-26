package hr.smartbudget.app

import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.data.Rezervacija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.domain.TekuciRacun
import hr.smartbudget.app.ui.screens.KARTICE_ANALITIKE
import hr.smartbudget.app.ui.screens.poredaj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TekuciRacunTest {
    private val zona = ZoneId.systemDefault()
    private val dan = LocalDate.of(2026, 11, 26)
    private fun t(d: LocalDate, sat: Int) = d.atStartOfDay(zona).plusHours(sat.toLong()).toInstant().toEpochMilli()
    private fun tx(iznos: Long, kada: Long, prihod: Boolean = false, racun: String = "Tekući račun") =
        Transakcija(tip = if (prihod) TipTransakcije.PRIHOD else TipTransakcije.TROSAK, iznosCenti = iznos, datumVrijeme = kada, kategorijaId = 1, nacinPlacanja = racun)

    private val racun = Racun(id = 1, naziv = "Tekući račun", bojaHex = "#000", ikonaNaziv = "bank", tekuci = true, sidroCenti = 350_000, sidroMillis = t(dan, 10))

    @Test fun unesenoStanjePaPrihodITrosak() {
        // 3.500 € na računu, zatim plaća 2.000 € i troškovi ciklusa 2.300 € (ciklus u minusu 300 €)
        val lista = listOf(tx(200_000, t(dan, 11), prihod = true), tx(230_000, t(dan, 12)))
        assertEquals(320_000L, TekuciRacun.stanje(racun, lista))
        // Unosi s drugih računa (kartica, gotovina) ne mijenjaju tekući račun
        assertEquals(320_000L, TekuciRacun.stanje(racun, lista + tx(5_000, t(dan, 13), racun = "Gotovina")))
    }

    @Test fun naknadnoUpisanStarijiUnosNeMijenjaDanasnjeStanje() {
        // Jučerašnja kava upisana tek sada: banka ju je već skinula, uneseno stanje ju sadrži
        val jucer = tx(300, t(dan.minusDays(1), 9))
        assertEquals(350_000L, TekuciRacun.stanje(racun, listOf(jucer)))
        // ...ali stanje na kraju predjučer je bilo veće za tu kavu
        assertEquals(350_300L, TekuciRacun.stanje(racun, listOf(jucer), t(dan.minusDays(2), 23)))
    }

    @Test fun bezUnesenogStanjaPocetnoPlusUnosi() {
        val r = racun.copy(sidroCenti = null, sidroMillis = null, pocetnoStanjeCenti = 10_000)
        assertEquals(5_000L, TekuciRacun.stanje(r, listOf(tx(5_000, t(dan, 9)))))
    }

    @Test fun rezervacijeIUpozorenje() {
        val rez = listOf(
            Rezervacija(1, 50_000, "porez", t(dan, 8)),
            Rezervacija(2, 20_000, "", t(dan, 8), oslobodenoMillis = t(dan, 9)),
        )
        assertEquals(50_000L, TekuciRacun.rezervirano(rez))
        assertEquals(70_000L, TekuciRacun.rezervirano(rez, t(dan, 8)))
        // Potrošena plaća 2.000 € i 3.000 € od 3.500 €: ostaje 500 € = rezervirano, još nije zadiranje
        val tx = listOf(tx(200_000, t(dan, 11), prihod = true), tx(500_000, t(dan, 12)))
        val s = TekuciRacun.stanjeTekuceg(listOf(racun), tx, rez)!!
        assertEquals(50_000L, s.stanje)
        assertEquals(0L, s.slobodno)
        assertFalse(s.zadireURezervu)
        // Još 10 €: zadire u rezervu
        val s2 = TekuciRacun.stanjeTekuceg(listOf(racun), tx + tx(1_000, t(dan, 13)), rez)!!
        assertTrue(s2.zadireURezervu)
        assertEquals(1_000L, s2.manjak)
        // Oslobađanjem rezervacije upozorenje nestaje
        val s3 = TekuciRacun.stanjeTekuceg(listOf(racun), tx + tx(1_000, t(dan, 13)), rez.map { it.copy(oslobodenoMillis = t(dan, 14)) })!!
        assertFalse(s3.zadireURezervu)
    }

    @Test fun graPoDanimaKumulativno() {
        val lista = listOf(tx(10_000, t(dan.minusDays(1), 9)), tx(200_000, t(dan.plusDays(1), 9), prihod = true), tx(5_000, t(dan.plusDays(2), 9)))
        val tocke = TekuciRacun.poDanima(racun, lista, emptyList(), dan.minusDays(2), dan.plusDays(2), zona)
        assertEquals(listOf(360_000L, 350_000L, 350_000L, 550_000L, 545_000L), tocke.map { it.stanje })
        // Svaka točka jednaka izravnom izračunu
        tocke.forEach { p -> assertEquals(TekuciRacun.stanje(racun, lista, t(p.datum, 24) - 1), p.stanje) }
    }

    @Test fun novaKarticaNaZadanoMjesto() {
        // Spremljeni raspored iz starije verzije (bez "tekuci"): nova kartica dolazi prva, kao u zadanom
        val stari = KARTICE_ANALITIKE.map { it.kljuc }.filter { it != "tekuci" }.reversed()
        val r = poredaj(KARTICE_ANALITIKE, stari).map { it.kljuc }
        assertEquals("tekuci", r.first())
        assertEquals(stari, r.drop(1))
    }
}
