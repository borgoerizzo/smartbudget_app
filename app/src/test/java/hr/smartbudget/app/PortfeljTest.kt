package hr.smartbudget.app

import hr.smartbudget.app.data.Pozicija
import hr.smartbudget.app.data.PromjenaPozicije
import hr.smartbudget.app.data.Trziste
import hr.smartbudget.app.data.VrstaUlaganja
import hr.smartbudget.app.domain.Portfelj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortfeljTest {
    @Test fun kolicineSDijelovima() {
        assertEquals(10_000_000L, Portfelj.parsirajKolicinu("10"))
        assertEquals(500_000L, Portfelj.parsirajKolicinu("0,5"))
        assertEquals(250_000L, Portfelj.parsirajKolicinu("0.25"))
        assertEquals(1_250_500_000L, Portfelj.parsirajKolicinu("1.250,5"))
        assertEquals(1_234_567L, Portfelj.parsirajKolicinu("1,234567"))
        assertNull(Portfelj.parsirajKolicinu("1,2345678")) // više od 6 decimala
        assertNull(Portfelj.parsirajKolicinu("-1"))
        assertNull(Portfelj.parsirajKolicinu("abc"))
        assertEquals("1.250,5", Portfelj.kolicina(1_250_500_000L))
        assertEquals("12", Portfelj.kolicina(12_000_000L))
    }

    @Test fun peniPostajuFunte() {
        assertEquals("GBP" to 0.01, Portfelj.normalizirajValutu("GBp"))
        assertEquals("USD" to 1.0, Portfelj.normalizirajValutu("USD"))
        assertEquals("EUR" to 1.0, Portfelj.normalizirajValutu("eur"))
    }

    private val apple = Pozicija(id = 1, simbol = "AAPL", naziv = "Apple", vrsta = "DIONICE", valuta = "USD")

    @Test fun stanjeIzKupnjiIProdaja() {
        val p = listOf(
            PromjenaPozicije(id = 1, pozicijaId = 1, kolicinaMikro = 10_000_000, epochDan = 100, ulozenoCenti = 150_000),
            PromjenaPozicije(id = 2, pozicijaId = 1, kolicinaMikro = -4_000_000, epochDan = 120),
            PromjenaPozicije(id = 3, pozicijaId = 9, kolicinaMikro = 1_000_000, epochDan = 120),
        )
        val s = Portfelj.stanja(listOf(apple), p).single()
        assertEquals(6_000_000L, s.kolicinaMikro)
        assertEquals(listOf(2L, 1L), s.promjene.map { it.id }) // najnovije prve, samo svoje
        assertEquals(150_000L, s.ulozeno)
        assertTrue(s.ulozenoPotpuno)
        val bez = Portfelj.stanja(listOf(apple), p + PromjenaPozicije(id = 4, pozicijaId = 1, kolicinaMikro = 1_000_000, epochDan = 130)).single()
        assertFalse(bez.ulozenoPotpuno)
    }

    @Test fun vrijednostPoDanimaPopunjavaVikende() {
        val s = Portfelj.stanja(listOf(apple), listOf(PromjenaPozicije(pozicijaId = 1, kolicinaMikro = 2_000_000, epochDan = 1))).single()
        val d = LocalDate.of(2026, 11, 13) // petak
        val cijene = mapOf("AAPL" to listOf(d.minusDays(1) to 90.0, d to 100.0, d.plusDays(3) to 110.0))
        val v = Portfelj.vrijednostPoDanima(listOf(s), cijene, { if (it == "USD") 0.5 else null }, d, d.plusDays(3))
        // 2 kom. × cijena × 0,5 (USD → osnovna); subota i nedjelja zadržavaju petkovu cijenu
        assertEquals(listOf(100.0, 100.0, 100.0, 110.0), v.map { it.second })
        // Bez tečaja za valutu nema grafa
        assertTrue(Portfelj.vrijednostPoDanima(listOf(s), cijene, { null }, d, d.plusDays(3)).isEmpty())
    }

    @Test fun pretragaPreskaceKriptoINepoznato() {
        val json = """
            {"quotes":[
              {"symbol":"AAPL","longname":"Apple Inc.","quoteType":"EQUITY","exchDisp":"NASDAQ"},
              {"symbol":"VWCE.DE","shortname":"Vanguard FTSE All-World","quoteType":"ETF","exchange":"GER"},
              {"symbol":"GC=F","shortname":"Gold","quoteType":"FUTURE"},
              {"symbol":"BTC-USD","shortname":"Bitcoin","quoteType":"CRYPTOCURRENCY"},
              {"symbol":"X","quoteType":"OPTION"}
            ]}
        """.trimIndent()
        val r = Trziste.parsirajPretragu(json)
        assertEquals(listOf("AAPL", "VWCE.DE", "GC=F"), r.map { it.simbol })
        assertEquals(listOf(VrstaUlaganja.DIONICE, VrstaUlaganja.ETF, VrstaUlaganja.ROBA), r.map { it.vrsta })
        assertEquals("Apple Inc.", r[0].naziv)
        assertEquals("GER", r[1].burza)
    }

    @Test fun dnevnaPovijestPoDatumuBurze() {
        // 1. 12. 2026. 14:30 UTC = 9:30 u New Yorku; zadnja cijena dana pobjeđuje, praznine se preskaču
        val json = """
            {"chart":{"result":[{"meta":{"exchangeTimezoneName":"America/New_York"},
              "timestamp":[1795962600,1795980000,1796049000],
              "indicators":{"quote":[{"close":[100.0,101.0,null]}]}}]}}
        """.trimIndent()
        val p = Trziste.parsirajDnevnuPovijest(json)
        assertEquals(1, p.size)
        assertEquals(101.0, p[0].second, 1e-9)
    }
}
