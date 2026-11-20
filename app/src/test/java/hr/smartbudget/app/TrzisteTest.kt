package hr.smartbudget.app

import hr.smartbudget.app.data.FormatTrzista
import hr.smartbudget.app.data.OcjenaAnaliticara
import hr.smartbudget.app.data.Trziste
import hr.smartbudget.app.data.VrstaUlaganja
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrzisteTest {
    private val lista = """
        {"finance":{"result":[{"title":"Most Actives","quotes":[
          {"symbol":"NVDA","longName":"NVIDIA Corporation","currency":"USD","regularMarketPrice":180.5,"regularMarketChange":-2.5,
           "regularMarketChangePercent":-1.366,"regularMarketVolume":200000000,"averageDailyVolume10Day":150000000,
           "averageDailyVolume3Month":300000000,"marketCap":4.4E12,"trailingPE":51.2,"averageAnalystRating":"1.3 - Strong Buy",
           "fiftyTwoWeekLow":86.6,"fiftyTwoWeekHigh":212.2,"fullExchangeName":"NasdaqGS"},
          {"symbol":"INTC","shortName":"Intel","currency":"USD","regularMarketPrice":37.1,"regularMarketChange":0.4,
           "regularMarketChangePercent":1.09,"regularMarketVolume":250000000,"averageDailyVolume10Day":90000000,
           "averageDailyVolume3Month":100000000,"averageAnalystRating":"2.5 - Hold"},
          {"symbol":"BEZCIJENE"}
        ]}],"error":null}}
    """.trimIndent()

    @Test fun parsiraGotovuListu() {
        val k = Trziste.parsirajListu(lista, VrstaUlaganja.DIONICE)
        assertEquals(listOf("NVDA", "INTC"), k.map { it.simbol }) // bez cijene se preskače
        assertEquals("NVIDIA Corporation", k[0].naziv)
        assertEquals("Intel", k[1].naziv) // shortName kad nema longName
        assertEquals(1.3, k[0].ocjena!!.vrijednost, 1e-9)
        assertEquals("Jaka kupnja", k[0].ocjena!!.oznaka)
        assertEquals(51.2, k[0].pe!!, 1e-9)
        assertNull(k[1].trzisnaKap)
    }

    @Test fun najtrgovanijePoRazdoblju() {
        val k = Trziste.parsirajListu(lista, VrstaUlaganja.DIONICE)
        assertEquals(listOf("INTC", "NVDA"), Trziste.poPrometu(k, 1).map { it.simbol })
        assertEquals(listOf("NVDA", "INTC"), Trziste.poPrometu(k, 10).map { it.simbol })
        assertEquals(listOf("NVDA", "INTC"), Trziste.poPrometu(k, 90).map { it.simbol })
    }

    @Test fun preporukeSamoKupnjaIJakaKupnja() {
        val k = Trziste.parsirajListu(lista, VrstaUlaganja.DIONICE)
        // INTC je "Drži" (2.5) i ne ulazi; duplikati se uklanjaju.
        assertEquals(listOf("NVDA"), Trziste.preporuke(k + k).map { it.simbol })
    }

    @Test fun ocjenaAnaliticara() {
        assertEquals("Kupnja", OcjenaAnaliticara.parsiraj("1.8 - Buy")!!.oznaka)
        assertEquals("Drži", OcjenaAnaliticara.parsiraj("3.0 - Hold")!!.oznaka)
        assertEquals("Prodaja", OcjenaAnaliticara.parsiraj("4.8 - Sell")!!.oznaka)
        assertNull(OcjenaAnaliticara.parsiraj(null))
        assertNull(OcjenaAnaliticara.parsiraj("n/a"))
        assertNull(OcjenaAnaliticara.parsiraj("7.0 - ?"))
    }

    @Test fun sparkCuvaZadaniRedoslijedIPromjenu() {
        val json = """
            {"spark":{"result":[
              {"symbol":"SI=F","response":[{"meta":{"currency":"USD","symbol":"SI=F","regularMarketPrice":40.0,"chartPreviousClose":50.0},
               "timestamp":[1,2,3],"indicators":{"quote":[{"close":[49.0,null,40.0]}]}}]},
              {"symbol":"GC=F","response":[{"meta":{"currency":"USD","symbol":"GC=F","regularMarketPrice":110.0,"chartPreviousClose":100.0},
               "timestamp":[1,2],"indicators":{"quote":[{"close":[101.0,110.0]}]}}]}
            ],"error":null}}
        """.trimIndent()
        val r = Trziste.parsirajSpark(json, linkedMapOf("GC=F" to "Zlato", "SI=F" to "Srebro"), VrstaUlaganja.ROBA)
        assertEquals(listOf("Zlato", "Srebro"), r.map { it.first.naziv })
        assertEquals(10.0, r[0].first.promjenaPosto, 1e-9)
        assertEquals(-20.0, r[1].first.promjenaPosto, 1e-9)
        assertEquals(listOf(49.0, 40.0), r[1].second) // praznine se preskaču
    }

    @Test fun grafPreskaceRupeIZadrzavaPodatkeIzListe() {
        val json = """
            {"chart":{"result":[{"meta":{"currency":"USD","symbol":"NVDA","regularMarketPrice":181.0,"chartPreviousClose":183.0,
              "previousClose":183.0,"fiftyTwoWeekHigh":212.2},
              "timestamp":[100,200,300],"indicators":{"quote":[{"close":[182.0,null,181.0]}]}}],"error":null}}
        """.trimIndent()
        val osnova = Trziste.parsirajListu(lista, VrstaUlaganja.DIONICE)[0]
        val g = Trziste.parsirajGraf(json, osnova, VrstaUlaganja.DIONICE)
        assertEquals(listOf(100_000L, 300_000L), g.tocke.map { it.vrijeme })
        assertEquals(183.0, g.pocetak!!, 1e-9)
        assertEquals(181.0, g.kotacija.cijena, 1e-9)
        assertEquals(-2.0, g.kotacija.promjena, 1e-9)
        assertEquals("NVIDIA Corporation", g.kotacija.naziv)
        assertEquals(51.2, g.kotacija.pe!!, 1e-9) // P/E iz liste ostaje
        assertTrue(g.kotacija.ocjena != null)
    }

    @Test fun godisnjiGrafNeMijenjaJucerasnjeZatvaranje() {
        // Za razdoblje 1y "chartPreviousClose" je cijena od prije godinu dana, ne jučerašnje zatvaranje.
        val json = """
            {"chart":{"result":[{"meta":{"currency":"USD","symbol":"NVDA","range":"1y","regularMarketPrice":181.0,"chartPreviousClose":90.0},
              "timestamp":[100,200],"indicators":{"quote":[{"close":[91.0,181.0]}]}}],"error":null}}
        """.trimIndent()
        val osnova = Trziste.parsirajListu(lista, VrstaUlaganja.DIONICE)[0]
        val g = Trziste.parsirajGraf(json, osnova, VrstaUlaganja.DIONICE)
        assertNull(osnova.prethodnoZatvaranje) // lista ga ovdje nema …
        assertNull(g.kotacija.prethodnoZatvaranje) // … i ne smije postati 90
        assertEquals(osnova.promjenaPosto, g.kotacija.promjenaPosto, 1e-9)
        assertEquals(90.0, g.pocetak!!, 1e-9) // početak razdoblja ostaje za graf
    }

    @Test fun hrvatskiFormat() {
        assertEquals("1.234,57 $", FormatTrzista.cijena(1234.567, "USD"))
        assertEquals("0,1235 €", FormatTrzista.cijena(0.12345, "EUR"))
        assertEquals("4,133 %", FormatTrzista.cijena(4.1330004, "USD", prinos = true))
        assertEquals("+1,09 %", FormatTrzista.promjenaPosto(1.0912))
        assertEquals("−1,37 %", FormatTrzista.promjenaPosto(-1.366))
        assertEquals("4,40 bil.", FormatTrzista.veliki(4.4e12))
        assertEquals("200,00 mil.", FormatTrzista.veliki(2e8))
    }
}
