package hr.smartbudget.app

import hr.smartbudget.app.export.FormatIzvoza
import hr.smartbudget.app.export.Izvoz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class IzvozTablicaTest {
    private val redovi = listOf(
        listOf(Izvoz.Celija.Tekst("Valuta"), Izvoz.Celija.Tekst("Dolari; putovanje"), Izvoz.Celija.Broj(1234.5), Izvoz.Celija.Broj(null)),
        listOf(Izvoz.Celija.Tekst("Državni zapis/obveznica"), Izvoz.Celija.Tekst("RHMF-T-722N"), Izvoz.Celija.Broj(9700.0), Izvoz.Celija.Broj(3.1)),
    )

    @Test fun csvSaZarezomINavodnicima() {
        val out = ByteArrayOutputStream()
        Izvoz.tablica(FormatIzvoza.CSV, "Štednja", listOf("Vrsta", "Naziv", "Vrijednost", "Prinos"), redovi, listOf(10, 10, 10, 10), out)
        val tekst = out.toByteArray().drop(3).toByteArray().toString(Charsets.UTF_8) // bez BOM-a
        val linije = tekst.trimEnd().split("\r\n")
        assertEquals("Vrsta;Naziv;Vrijednost;Prinos", linije[0])
        assertEquals("Valuta;\"Dolari; putovanje\";1234,50;", linije[1])
        assertEquals("Državni zapis/obveznica;RHMF-T-722N;9700,00;3,10", linije[2])
    }

    @Test fun xlsxImaListIBrojeve() {
        val out = ByteArrayOutputStream()
        Izvoz.tablica(FormatIzvoza.XLSX, "Štednja", listOf("Vrsta", "Naziv", "Vrijednost", "Prinos"), redovi, listOf(10, 10, 10, 10), out)
        val datoteke = mutableMapOf<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                datoteke[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue("xl/worksheets/sheet1.xml" in datoteke)
        assertTrue(datoteke.getValue("xl/workbook.xml").contains("name=\"Štednja\""))
        val list = datoteke.getValue("xl/worksheets/sheet1.xml")
        assertTrue(list.contains("<c r=\"C2\" s=\"2\"><v>1234.5</v></c>"))
        assertTrue(list.contains("RHMF-T-722N"))
        assertTrue(!list.contains("r=\"D2\"")) // prazna ćelija se ne zapisuje
    }
}
