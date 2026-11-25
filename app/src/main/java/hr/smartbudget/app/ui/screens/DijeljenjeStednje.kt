package hr.smartbudget.app.ui.screens

import android.content.Context
import android.content.Intent
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.data.FormatTrzista
import hr.smartbudget.app.data.UplataCilja
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Portfelj
import hr.smartbudget.app.domain.StanjeNovcanika
import hr.smartbudget.app.domain.StanjePozicije
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.domain.ustedjenoCilja
import hr.smartbudget.app.export.FormatIzvoza
import hr.smartbudget.app.export.Izvoz
import hr.smartbudget.app.ui.CijeneUlaganja
import hr.smartbudget.app.ui.StanjeTecajeva
import java.io.OutputStream
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/*
 * Dijeljenje štednje kao teksta (svaka vrsta zasebno ili sve zajedno) i izvoz štednje u Excel/CSV.
 * Iznosi se preračunavaju u valutu aplikacije po istim tečajevima i cijenama kao na zaslonu.
 */

fun podijeliTekst(ctx: Context, naslov: String, tekst: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, naslov)
        putExtra(Intent.EXTRA_TEXT, tekst)
    }
    ctx.startActivity(Intent.createChooser(send, naslov).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun iznosOsnovna(v: Double?) = v?.let { Valute.iznos(it, osnovnaValuta()) } ?: "—"

fun redakNovcanika(s: StanjeNovcanika, t: StanjeTecajeva): String {
    val o = osnovnaValuta()
    val v = if (s.valuta.kod == o.kod) null else t.pretvori(s.stanje, s.valuta, o)
    return "${s.novcanik.naziv}: ${Valute.iznos(s.stanje, s.valuta)}" + (v?.let { " ≈ " + Valute.iznos(it, o) } ?: "") +
        (s.novcanik.rokEpochDan?.let { " · rok ${HrDatum.puni(LocalDate.ofEpochDay(it))}" } ?: "")
}

fun redakPozicije(s: StanjePozicije, c: CijeneUlaganja, t: StanjeTecajeva): String {
    val k = c.kotacije[s.pozicija.simbol]
    val v = vrijednostPozicije(s, k, t)
    return if (s.pozicija.rucno) {
        "${s.pozicija.naziv}: ${iznosOsnovna(v)} · ${opisRucnog(s)}"
    } else {
        "${s.pozicija.naziv} (${s.pozicija.simbol}): ${Portfelj.kolicina(s.kolicinaMikro)} × " +
            (k?.let { FormatTrzista.cijena(it.cijena, it.valuta) } ?: "—") + " ≈ ${iznosOsnovna(v)}" +
            (k?.ocjena?.let { " · analitičari: ${it.oznaka} ${FormatTrzista.broj(it.vrijednost, 1)}" } ?: "") +
            (s.pozicija.dospijeceEpochDan?.let { " · rok ${HrDatum.puni(LocalDate.ofEpochDay(it))}" } ?: "")
    }
}

fun tekstNovcanika(lista: List<StanjeNovcanika>, t: StanjeTecajeva): String {
    val o = osnovnaValuta()
    val ukupno = lista.sumOf { if (it.valuta.kod == o.kod) Valute.uBroj(it.stanje, it.valuta) else t.pretvori(it.stanje, it.valuta, o) ?: 0.0 }
    return buildString {
        appendLine("Štednja u valutama (SmartBudget, ${HrDatum.puni(LocalDate.now())})")
        lista.forEach { appendLine("• " + redakNovcanika(it, t)) }
        append("Ukupno ≈ ${Valute.iznos(ukupno, o)}")
    }
}

fun tekstUlaganja(lista: List<StanjePozicije>, c: CijeneUlaganja, t: StanjeTecajeva): String = buildString {
    appendLine("Moja ulaganja (SmartBudget, ${HrDatum.puni(LocalDate.now())})")
    lista.forEach { appendLine("• " + redakPozicije(it, c, t)) }
    append("Ukupno ≈ ${Valute.iznos(vrijednostUlaganja(lista, c, t), osnovnaValuta())}")
}

fun tekstCiljeva(ciljevi: List<CiljStednje>, uplate: List<UplataCilja>, t: StanjeTecajeva): String = buildString {
    val o = osnovnaValuta()
    appendLine("Ciljevi štednje (SmartBudget, ${HrDatum.puni(LocalDate.now())})")
    var ukupno = 0L
    ciljevi.forEach { c ->
        val u = ustedjenoCilja(c, uplate) { j, v -> uOsnovnuValutu(j, v, t) }.centi
        ukupno += u
        appendLine(
            "• ${c.naziv}: ${Valute.iznos(u, o)} od ${Valute.iznos(c.ciljCenti, o)}" +
                (if (c.ciljCenti > 0) " (${u * 100 / c.ciljCenti} %)" else "") +
                (c.rokEpochDan?.let { " · rok ${HrDatum.puni(LocalDate.ofEpochDay(it))}" } ?: ""),
        )
    }
    append("Ukupno ušteđeno: ${Valute.iznos(ukupno, o)}")
}

/** Sva štednja kao jedan tekst (ciljevi, valute, ulaganja), za brzo dijeljenje. */
fun tekstSveStednje(
    ciljevi: List<CiljStednje>, uplate: List<UplataCilja>, novcanici: List<StanjeNovcanika>,
    pozicije: List<StanjePozicije>, c: CijeneUlaganja, t: StanjeTecajeva,
): String = listOfNotNull(
    ciljevi.takeIf { it.isNotEmpty() }?.let { tekstCiljeva(it, uplate, t) },
    novcanici.takeIf { it.isNotEmpty() }?.let { tekstNovcanika(it, t) },
    pozicije.filter { it.kolicinaMikro > 0 }.takeIf { it.isNotEmpty() }?.let { tekstUlaganja(it, c, t) },
).joinToString("\n\n")

/** Izvoz sve štednje u jednu tablicu: vrsta, naziv, oznaka, količina, valuta, cijena, vrijednost, uloženo, dobit, rok. */
fun izveziStednju(
    format: FormatIzvoza, out: OutputStream,
    ciljevi: List<CiljStednje>, uplate: List<UplataCilja>, novcanici: List<StanjeNovcanika>,
    pozicije: List<StanjePozicije>, c: CijeneUlaganja, t: StanjeTecajeva,
): Int {
    val o = osnovnaValuta()
    fun tx(s: String) = Izvoz.Celija.Tekst(s)
    fun br(v: Double?) = Izvoz.Celija.Broj(v)
    fun datum(d: Long?) = tx(d?.let { HrDatum.puni(LocalDate.ofEpochDay(it)) } ?: "")
    val redovi = mutableListOf<List<Izvoz.Celija>>()
    ciljevi.forEach { cilj ->
        val u = ustedjenoCilja(cilj, uplate) { j, v -> uOsnovnuValutu(j, v, t) }.centi / 100.0
        redovi += listOf(tx("Cilj štednje"), tx(cilj.naziv), tx(""), br(null), tx(o.kod), br(null), br(u), br(null), br(null), datum(cilj.rokEpochDan), tx("cilj ${Valute.iznos(cilj.ciljCenti, o)}"))
    }
    novcanici.forEach { s ->
        val v = if (s.valuta.kod == o.kod) Valute.uBroj(s.stanje, s.valuta) else t.pretvori(s.stanje, s.valuta, o)
        val ul = s.ulozeno?.takeIf { s.ulozenoPotpuno }?.div(100.0)
        redovi += listOf(
            tx(if (s.valuta.kripto) "Kriptovaluta" else "Valuta"), tx(s.novcanik.naziv), tx(s.valuta.kod),
            br(Valute.uBroj(s.stanje, s.valuta)), tx(s.valuta.kod), br(null), br(v), br(ul), br(if (ul != null && v != null) v - ul else null),
            datum(s.novcanik.rokEpochDan), tx(""),
        )
    }
    pozicije.filter { it.kolicinaMikro > 0 }.forEach { s ->
        val k = c.kotacije[s.pozicija.simbol]
        val v = vrijednostPozicije(s, k, t)
        val ul = s.ulozeno?.takeIf { s.ulozenoPotpuno }?.div(100.0)
        val vrsta = runCatching { hr.smartbudget.app.data.VrstaUlaganja.valueOf(s.pozicija.vrsta).naziv.replace("​", "") }.getOrDefault("Ulaganje")
        redovi += listOf(
            tx(if (s.pozicija.rucno) "Državni zapis/obveznica" else vrsta), tx(s.pozicija.naziv), tx(s.pozicija.simbol),
            br(if (s.pozicija.rucno) null else s.kolicina), tx(k?.valuta ?: s.pozicija.valuta), br(k?.cijena),
            br(v), br(ul), br(if (ul != null && v != null) v - ul else null), datum(s.pozicija.dospijeceEpochDan),
            tx(listOfNotNull(k?.ocjena?.let { "analitičari: ${it.oznaka} ${FormatTrzista.broj(it.vrijednost, 1)}" }, s.prinosGodisnji?.let { "prinos ${FormatTrzista.broj(it, 2)} % god." }).joinToString(" · ")),
        )
    }
    Izvoz.tablica(
        format, "Štednja",
        listOf("Vrsta", "Naziv", "Oznaka", "Količina", "Valuta", "Cijena", "Vrijednost (${o.kod})", "Uloženo (${o.kod})", "Dobit/gubitak (${o.kod})", "Rok / dospijeće", "Napomena"),
        redovi, listOf(18, 32, 16, 12, 9, 12, 16, 14, 18, 15, 40), out,
    )
    return redovi.size
}

/**
 * Redak štednje s višestrukim odabirom: dodir otvara (ili tijekom odabira označava), dugi pritisak pokreće odabir.
 * [odabran] null = odabir nije u tijeku.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
internal fun RedSOdabirom(
    odabran: Boolean?,
    opis: String,
    onClick: () -> Unit,
    onDugi: () -> Unit,
    sadrzaj: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(if (odabran == true) hr.smartbudget.app.ui.theme.Neon.Cijan.copy(alpha = .12f) else hr.smartbudget.app.ui.theme.Neon.Kartica)
            .combinedClickable(onClickLabel = if (odabran == null) opis else "Odaberi", onLongClickLabel = "Odaberi više", onLongClick = onDugi, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (odabran != null) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.padding(end = 10.dp).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (odabran) hr.smartbudget.app.ui.theme.Neon.Cijan else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.5.dp, if (odabran) hr.smartbudget.app.ui.theme.Neon.Cijan else hr.smartbudget.app.ui.theme.Neon.TekstSiv, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (odabran) androidx.compose.material3.Icon(hr.smartbudget.app.ui.Ikone.ui("check"), null, androidx.compose.ui.Modifier.size(14.dp), tint = hr.smartbudget.app.ui.theme.Neon.NaGumbu)
            }
        }
        sadrzaj()
    }
}
