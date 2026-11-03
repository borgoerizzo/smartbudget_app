package hr.smartbudget.app.export

import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.domain.predznaceniCenti
import hr.smartbudget.app.domain.vrijeme
import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class FormatIzvoza(val nastavak: String, val mime: String, val naziv: String) {
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (.xlsx)"),
    CSV("csv", "text/csv", "CSV (.csv)"),
}

/**
 * Izvoz transakcija u Excel. Stupci: Datum, Vrijeme, Tip, Kategorija, Iznos (€), Način plaćanja, Napomena.
 * Troškovi se izvoze kao negativni iznosi, tako da zbroj stupca daje neto stanje.
 */
object Izvoz {
    val zaglavlje = listOf("Datum", "Vrijeme", "Tip", "Kategorija", "Iznos (€)", "Način plaćanja", "Napomena")

    private fun tip(t: TransakcijaSKategorijom) = if (t.transakcija.tip == TipTransakcije.PRIHOD) "Prihod" else "Trošak"

    fun zapisi(format: FormatIzvoza, transakcije: List<TransakcijaSKategorijom>, out: OutputStream) = when (format) {
        FormatIzvoza.CSV -> csv(transakcije, out)
        FormatIzvoza.XLSX -> xlsx(transakcije, out)
    }

    /**
     * CSV s UTF-8 BOM-om (Excel tada ispravno prikazuje č, ć, ž, š, đ), separator ';'
     * i decimalni zarez, kako Excel očekuje u hrvatskim regionalnim postavkama.
     */
    fun csv(transakcije: List<TransakcijaSKategorijom>, out: OutputStream) {
        val sb = StringBuilder()
        fun redak(polja: List<String>) {
            sb.append(polja.joinToString(";") { p ->
                if (p.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + p.replace("\"", "\"\"") + "\"" else p
            }).append("\r\n")
        }
        redak(zaglavlje)
        transakcije.forEach { t ->
            redak(
                listOf(
                    HrDatum.puni(t.datum()),
                    HrDatum.vrijeme(t.vrijeme()),
                    tip(t),
                    t.kategorija.naziv,
                    BigDecimal.valueOf(t.predznaceniCenti, 2).toPlainString().replace('.', ','),
                    t.transakcija.nacinPlacanja,
                    t.transakcija.opis,
                ),
            )
        }
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Minimalna, ispravna .xlsx radna knjiga (Office Open XML) bez vanjskih biblioteka. */
    fun xlsx(transakcije: List<TransakcijaSKategorijom>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun dat(ime: String, sadrzaj: String) {
            zip.putNextEntry(ZipEntry(ime))
            zip.write(sadrzaj.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        dat(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
        )
        dat(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        )
        dat(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Transakcije" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        )
        dat(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
        )
        // Stilovi: 0 = obično, 1 = podebljano zaglavlje, 2 = iznos u €.
        dat(
            "xl/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00\ &quot;€&quot;"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>""",
        )
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sheet.append("<cols>")
        listOf(13, 9, 10, 22, 14, 16, 40).forEachIndexed { i, w ->
            sheet.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        sheet.append("</cols><sheetData>")
        fun tekst(ref: String, v: String, stil: Int = 0) =
            """<c r="$ref" t="inlineStr"${if (stil > 0) " s=\"$stil\"" else ""}><is><t xml:space="preserve">${xml(v)}</t></is></c>"""
        val stupci = "ABCDEFG"
        sheet.append("""<row r="1">""")
        zaglavlje.forEachIndexed { i, h -> sheet.append(tekst("${stupci[i]}1", h, 1)) }
        sheet.append("</row>")
        transakcije.forEachIndexed { idx, t ->
            val r = idx + 2
            sheet.append("""<row r="$r">""")
            sheet.append(tekst("A$r", HrDatum.puni(t.datum())))
            sheet.append(tekst("B$r", HrDatum.vrijeme(t.vrijeme())))
            sheet.append(tekst("C$r", tip(t)))
            sheet.append(tekst("D$r", t.kategorija.naziv))
            sheet.append("""<c r="E$r" s="2"><v>${BigDecimal.valueOf(t.predznaceniCenti, 2).toPlainString()}</v></c>""")
            sheet.append(tekst("F$r", t.transakcija.nacinPlacanja))
            sheet.append(tekst("G$r", t.transakcija.opis))
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        dat("xl/worksheets/sheet1.xml", sheet.toString())
        zip.finish()
        zip.flush()
    }

    private fun xml(s: String) = buildString {
        s.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }
}
