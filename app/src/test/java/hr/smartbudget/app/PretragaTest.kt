package hr.smartbudget.app

import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.Pretraga
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PretragaTest {
    private fun t(opis: String, kat: String, centi: Long, nacin: String = "Tekući račun") = TransakcijaSKategorijom(
        Transakcija(tip = TipTransakcije.TROSAK, iznosCenti = centi, datumVrijeme = 0, kategorijaId = 1, opis = opis, nacinPlacanja = nacin),
        Kategorija(1, kat, "#FFFFFF", "star", TipTransakcije.TROSAK),
    )

    @Test fun bezKvacicaIVelikihSlova() {
        assertTrue(Pretraga.odgovara(t("Čistoća", "Režije", 1000), "cistoca"))
        assertTrue(Pretraga.odgovara(t("Džeparac", "Nea", 500), "DŽEP"))
        assertTrue(Pretraga.odgovara(t("Spar", "Namirnice", 950), "rezije").not())
        assertTrue(Pretraga.odgovara(t("Struja", "Režije", 6820), "rezije"))
    }

    @Test fun poIznosu() {
        val stambeni = t("Stambeni kredit", "Dom i kućanstvo", 48000)
        assertTrue(Pretraga.odgovara(stambeni, "480"))
        assertTrue(Pretraga.odgovara(stambeni, "480,00"))
        assertTrue(Pretraga.odgovara(t("Visa", "Režije", 125050), "1.250,50"))
        assertTrue(Pretraga.odgovara(t("Visa", "Režije", 125050), "1250"))
        assertTrue(Pretraga.odgovara(t("Spar", "Namirnice", 4580), "45.80"))
    }

    @Test fun viseRijeciIKategorijaINacin() {
        assertTrue(Pretraga.odgovara(t("Lidl", "Namirnice", 5000, "Kartica"), "lidl kartica"))
        assertFalse(Pretraga.odgovara(t("Lidl", "Namirnice", 5000, "Kartica"), "lidl gotovina"))
        assertTrue(Pretraga.odgovara(t("", "Zabava", 1000), "zab"))
        assertTrue(Pretraga.odgovara(t("Bilo što", "X", 1), "   "))
    }
}
