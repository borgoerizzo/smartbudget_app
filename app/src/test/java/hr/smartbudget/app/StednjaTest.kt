package hr.smartbudget.app

import hr.smartbudget.app.data.KriptoCijene
import hr.smartbudget.app.data.Novcanik
import hr.smartbudget.app.data.PromjenaNovcanika
import hr.smartbudget.app.data.TecajnaLista
import hr.smartbudget.app.data.Tecajevi
import hr.smartbudget.app.data.UplataCilja
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.domain.kolicinaPoDanima
import hr.smartbudget.app.domain.stanjaNovcanika
import hr.smartbudget.app.domain.uplatePoMjesecima
import hr.smartbudget.app.domain.vrijednostPortfelja
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StednjaTest {
    private val danas = LocalDate.of(2026, 11, 18)
    private val hnb = TecajnaLista(danas, "226", mapOf("EUR" to 1.0, "USD" to 1.1, "CHF" to 0.92), 0)
    private val kripto = KriptoCijene(mapOf("BTC" to 60_000.0), emptyMap(), 0)

    @Test fun pretvorbaPrekoEura() {
        assertEquals(100.0, Tecajevi.pretvori(110.0, Valute.po("USD"), Valute.po("EUR"), hnb, kripto)!!, 1e-9)
        assertEquals(92.0, Tecajevi.pretvori(110.0, Valute.po("USD"), Valute.po("CHF"), hnb, kripto)!!, 1e-9)
        assertEquals(3_300.0, Tecajevi.pretvori(0.05, Valute.po("BTC"), Valute.po("USD"), hnb, kripto)!!, 1e-6)
        assertEquals(0.05, Tecajevi.pretvori(3_000.0, Valute.po("EUR"), Valute.po("BTC"), null, kripto)!!, 1e-12)
        assertNull(Tecajevi.pretvori(1.0, Valute.po("GBP"), Valute.po("EUR"), hnb, kripto))
    }

    @Test fun parsiranjeHnb() {
        val json = """[{"broj_tecajnice":"226","datum_primjene":"2026-11-18","srednji_tecaj":"1,098900","valuta":"USD"}]"""
        val l = Tecajevi.parsirajHnb(json, 0)
        assertEquals(1.0989, l.tecajevi.getValue("USD"), 1e-9)
        assertEquals(LocalDate.of(2026, 11, 18), l.datum)
        assertEquals(1.0, l.tecajevi.getValue("EUR"), 0.0)
    }

    @Test fun rezervaEcb() {
        val l = Tecajevi.parsirajEcb("""{"amount":1.0,"base":"EUR","date":"2026-11-17","rates":{"USD":1.0989,"CHF":0.9233}}""", 0)
        assertEquals(1.0989, l.tecajevi.getValue("USD"), 1e-9)
        assertEquals(1.95583, l.tecajevi.getValue("BAM"), 1e-9)
        assertEquals("ECB", l.izvor)
    }

    @Test fun kriptoUnosIFormat() {
        val btc = Valute.po("BTC")
        assertEquals(5_000_000L, Valute.parsiraj("0,05", btc))
        assertEquals(12_345L, Valute.parsiraj("0.00012345", btc))
        assertNull(Valute.parsiraj("0,000000001", btc))
        assertEquals("0,05 BTC", Valute.iznos(5_000_000L, btc))
        // Unos s desna kao kod ostalih iznosa: 500000 → 0,00500000, i natrag u jedinice
        assertEquals("0,00500000", Valute.iznosBezOznake(500_000L, 8))
        assertEquals("12,34567890", Valute.iznosBezOznake(1_234_567_890L, 8))
        assertEquals(500_000L, Valute.parsiraj("0,00500000", btc))
        assertEquals(1_234_567_890L, Valute.parsiraj("12,34567890", btc))
        assertEquals(123_456_789_000_000L, Valute.parsiraj(Valute.iznosBezOznake(123_456_789_000_000L, 8), btc))
        assertEquals("1.250,50 $", Valute.iznos(125_050L, Valute.po("USD")))
    }

    @Test fun portfeljPoDanima() {
        val n = Novcanik(1, "Bitcoin", "BTC")
        val s = stanjaNovcanika(
            listOf(n),
            listOf(
                PromjenaNovcanika(1, 1, 10_000_000, danas.minusDays(2).toEpochDay(), 500_000),
                PromjenaNovcanika(2, 1, 10_000_000, danas.toEpochDay(), 650_000),
            ),
        )
        assertEquals(20_000_000L, s[0].stanje)
        assertEquals(1_150_000L, s[0].ulozeno)
        val k = kolicinaPoDanima(s[0].promjene, danas.minusDays(3), danas)
        assertEquals(listOf(0L, 10_000_000L, 10_000_000L, 20_000_000L), k.map { it.second })
        val cijene = mapOf("BTC" to listOf(danas.minusDays(3) to 50_000.0, danas.minusDays(2) to 52_000.0, danas to 60_000.0))
        val v = vrijednostPortfelja(s, cijene, danas.minusDays(3), danas)
        assertEquals(listOf(0.0, 5_200.0, 5_200.0, 12_000.0), v.map { it.second })
    }

    @Test fun ciljUVisevalutama() {
        val c = hr.smartbudget.app.data.CiljStednje(1, "Put", 500_000, 20_000, null, "#FFFFFF", "plane")
        val u = listOf(
            UplataCilja(1, 1, 20_000, danas.toEpochDay()),
            UplataCilja(2, 1, 11_000, danas.toEpochDay(), "USD"),
            UplataCilja(3, 1, 1_000_000, danas.toEpochDay(), "BTC"),
            UplataCilja(4, 1, -500_000, danas.toEpochDay(), "BTC"),
            UplataCilja(5, 2, 99_999, danas.toEpochDay(), "USD"),
        )
        val r = hr.smartbudget.app.domain.ustedjenoCilja(c, u) { j, v -> Tecajevi.pretvori(Valute.uBroj(j, v), v, Valute.po("EUR"), hnb, kripto) }
        assertEquals(mapOf("USD" to 11_000L, "BTC" to 500_000L), r.valute)
        // 200 € + 110 $ (100 €) + 0,005 BTC (300 €)
        assertEquals(60_000L, r.centi)
        assertEquals(true, r.potpuno)
    }

    @Test fun uplateMjesecno() {
        val n = Novcanik(1, "Dolari", "USD")
        val s = stanjaNovcanika(listOf(n), listOf(PromjenaNovcanika(1, 1, 11_000, danas.toEpochDay())))
        val u = uplatePoMjesecima(
            listOf(UplataCilja(1, 1, 20_000, danas.toEpochDay()), UplataCilja(2, 1, 5_000, danas.minusMonths(1).toEpochDay())),
            s, { j, v -> Tecajevi.pretvori(Valute.uBroj(j, v), v, Valute.po("EUR"), hnb, kripto) }, 3, danas,
        )
        assertEquals(YearMonth.of(2026, 9), u.first().first)
        assertEquals(listOf(0.0, 50.0, 300.0), u.map { it.second })
    }
}
