package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.data.Rezervacija
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.Os
import hr.smartbudget.app.domain.StanjeTekuceg
import hr.smartbudget.app.domain.TockaRacuna
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.skrivaStednju
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private fun trenutak(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "${HrDatum.kratko(t.toLocalDate())} u ${HrDatum.vrijeme(t)}"
}

/**
 * Tekući račun u Štednji: stvarno stanje računa (uneseno ručno, mijenja se unosima), rezervirana sredstva
 * koja se ne smiju trošiti i slobodni iznos.
 */
@Composable
fun TekuciRacunKartica(
    s: StanjeTekuceg?,
    racuni: List<Racun>,
    onSpremiStanje: (Racun, Long) -> Unit,
    onSpremiRezervaciju: (Rezervacija) -> Unit,
    onOslobodi: (Rezervacija) -> Unit,
    onObrisiRezervaciju: (Rezervacija) -> Unit,
    onPostaviTekuci: (Racun) -> Unit,
) {
    var unosStanja by remember { mutableStateOf(false) }
    var rezervacija by remember { mutableStateOf<Rezervacija?>(null) }
    var novaRezervacija by remember { mutableStateOf(false) }
    var biramRacun by remember { mutableStateOf(false) }
    var oslobodjeneOtvorene by remember { mutableStateOf(false) }

    Kartica(sjaj = if (s?.zadireURezervu == true) Neon.Crvena else Neon.Cijan, razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ikone.ikona("bank"), null, Modifier.size(22.dp), tint = Neon.Cijan)
            Text("  Tekući račun", style = Tip.naslovKartice, modifier = Modifier.weight(1f))
            if (s != null && racuni.size > 1) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Odaberi tekući račun") { biramRacun = true }) {
                    Oznaka(s.racun.naziv, Neon.TekstSvijetliSiv)
                }
            }
        }
        if (s == null) {
            Text("Nijedan račun nije označen kao tekući.", style = Tip.malo)
            TonalniGumb("Odaberi tekući račun", { biramRacun = true }, Modifier.fillMaxWidth(), ikona = "bank", boja = Neon.Cijan)
        } else {
            Text("Stanje računa", style = Tip.oznaka)
            Text(
                Novac.stanje(s.stanje), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                color = if (s.stanje < 0) Neon.Crvena else Neon.Tekst, modifier = Modifier.skrivaStednju(),
            )
            Text(
                if (s.uneseno) "Uneseno ${trenutak(s.racun.sidroMillis!!)}: ${Novac.stanje(s.racun.sidroCenti!!)}. Od tada ga mijenjaju prihodi i troškovi na računu ${s.racun.naziv}."
                else "Stanje se računa iz početnog stanja i unosa. Unesite stvarno stanje s bankovne aplikacije da bude točno.",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
            )
            if (s.rezervirano > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Plocica("Rezervirano", Novac.eur(s.rezervirano), "${s.aktivne.size} ${if (s.aktivne.size == 1) "stavka" else "stavke"}", Neon.Jantar, Modifier.weight(1f))
                    Plocica("Slobodno", Novac.stanje(s.slobodno), "smije se potrošiti", if (s.slobodno < 0) Neon.Crvena else Neon.Zelena, Modifier.weight(1f))
                }
            }
            if (s.zadireURezervu) UpozorenjeRezerve(s)
            Text(
                "U ciklusu se troše samo prihodi uneseni u tom ciklusu. Ostatak tekućeg računa je štednja; ako ciklus ode u minus, minus umanjuje i ovo stanje.",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp, fontSize = 11.sp),
            )
            TonalniGumb("Unesi stanje računa", { unosStanja = true }, Modifier.fillMaxWidth(), ikona = "edit", boja = Neon.Cijan, visina = 46.dp)

            OznakaPolja("Rezervirana sredstva")
            if (s.aktivne.isEmpty()) {
                Text("Iznos koji je na računu za nešto određeno i ne smije se trošiti. Kad stanje padne ispod rezerviranog, zaslon postaje crven.", style = Tip.malo.copy(fontSize = 12.sp))
            }
            s.aktivne.forEach { r ->
                RedRezervacije(r, aktivna = true, onClick = { rezervacija = r }, onOslobodi = { onOslobodi(r) })
            }
            TonalniGumb("Rezerviraj iznos", { novaRezervacija = true }, Modifier.fillMaxWidth(), ikona = "lock", boja = Neon.Jantar, visina = 44.dp)
            val oslobodjene = s.rezervacije.filter { !it.aktivna }
            if (oslobodjene.isNotEmpty()) {
                Text(
                    (if (oslobodjeneOtvorene) "▾ " else "▸ ") + "Oslobođene (${oslobodjene.size})",
                    style = Tip.oznaka, color = Neon.TekstSiv,
                    modifier = Modifier.clickable { oslobodjeneOtvorene = !oslobodjeneOtvorene }.padding(vertical = 4.dp),
                )
                if (oslobodjeneOtvorene) oslobodjene.forEach { r -> RedRezervacije(r, aktivna = false, onClick = { rezervacija = r }, onOslobodi = {}) }
            }
        }
    }

    if (unosStanja && s != null) UnosStanjaDijalog(s.racun, s.stanje, { onSpremiStanje(s.racun, it); unosStanja = false }) { unosStanja = false }
    if (novaRezervacija) RezervacijaDijalog(null, { onSpremiRezervaciju(it); novaRezervacija = false }, null, null) { novaRezervacija = false }
    rezervacija?.let { r ->
        RezervacijaDijalog(
            r, { onSpremiRezervaciju(it); rezervacija = null },
            if (r.aktivna) ({ onOslobodi(r); rezervacija = null }) else null,
            { onObrisiRezervaciju(r); rezervacija = null },
        ) { rezervacija = null }
    }
    if (biramRacun) {
        AlertDialog(
            onDismissRequest = { biramRacun = false },
            containerColor = Neon.Sheet,
            title = { Text("Koji je račun tekući?", color = Neon.Tekst) },
            text = {
                Column {
                    racuni.forEach { r ->
                        Text(
                            (if (r.tekuci) "● " else "○ ") + r.naziv,
                            color = if (r.tekuci) Neon.Cijan else Neon.Tekst, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPostaviTekuci(r); biramRacun = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton({ biramRacun = false }) { Text("Zatvori", color = Neon.TekstSiv) } },
        )
    }
}

@Composable
private fun Plocica(naziv: String, iznos: String, opis: String, boja: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Neon.Ispuna).border(1.dp, Neon.RubSlab, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(naziv, style = Tip.oznaka)
        Text(iznos, style = Tip.iznos.copy(fontSize = 16.sp), color = boja, maxLines = 1, modifier = Modifier.skrivaStednju())
        Text(opis, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp), maxLines = 1)
    }
}

@Composable
private fun RedRezervacije(r: Rezervacija, aktivna: Boolean, onClick: () -> Unit, onOslobodi: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna)
            .clickable(onClickLabel = "Uredi rezervaciju", onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                Novac.eur(r.iznosCenti), style = Tip.iznos.copy(fontSize = 15.sp),
                color = if (aktivna) Neon.Jantar else Neon.TekstSiv, modifier = Modifier.skrivaStednju(),
            )
            Text(
                listOfNotNull(
                    r.napomena.takeIf { it.isNotBlank() },
                    "od ${trenutak(r.kreiranMillis)}",
                    r.oslobodenoMillis?.let { "oslobođeno ${trenutak(it)}" },
                ).joinToString(" · "),
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp), maxLines = 2,
            )
        }
        if (aktivna) TextButton(onOslobodi) { Text("Oslobodi", color = Neon.Zelena, fontWeight = FontWeight.Bold) }
    }
}

/** Crveno upozorenje: stanje tekućeg računa je ispod rezerviranog iznosa. */
@Composable
fun UpozorenjeRezerve(s: StanjeTekuceg, skriveno: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Neon.CrvenaJaka.copy(alpha = .22f))
            .border(1.5.dp, Neon.CrvenaJaka, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ikone.ui("warn"), null, Modifier.size(26.dp), tint = Neon.CrvenaJaka)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Zadirete u rezervirana sredstva!", color = Color(0xFFFFD6DE), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (skriveno) "Stanje tekućeg računa palo je ispod rezerviranog iznosa."
                else "Potrošeno je ${Novac.eur(s.manjak)} od ${Novac.eur(s.rezervirano)} rezerviranih. Stanje računa: ${Novac.stanje(s.stanje)}.",
                color = Color(0xFFFFC2CE), fontSize = 13.sp, lineHeight = 18.sp,
            )
            Text(
                "Zaslon ostaje crven dok stanje ne bude ponovno barem jednako rezerviranom ili dok ne oslobodite rezervaciju.",
                color = Color(0xFFFFC2CE).copy(alpha = .8f), fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
    }
}

/** Unos stvarnog stanja računa (kakvo je sada na banci). */
@Composable
fun UnosStanjaDijalog(racun: Racun, trenutno: Long, onSpremi: (Long) -> Unit, onZatvori: () -> Unit) {
    var iznos by remember { mutableStateOf(Novac.broj(kotlin.math.abs(trenutno))) }
    var minus by remember { mutableStateOf(trenutno < 0) }
    var greska by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        title = { Text("Stanje: ${racun.naziv}", color = Neon.Tekst) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Upišite stanje kakvo sada vidite u banci. Prihodi i troškovi uneseni na ovaj račun od ovog trenutka ga mijenjaju; " +
                        "naknadno upisani stariji unosi ne mijenjaju današnje stanje.",
                    color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
                )
                NeonPolje(
                    iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true,
                    stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (minus) Neon.Crvena else Neon.Tekst, fontFeatureSettings = "tnum"),
                )
                Segmenti(listOf("U plusu", "U minusu"), if (minus) 1 else 0, { minus = it == 1 }, boje = listOf(Neon.Zelena, Neon.Crvena))
                greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val c = Novac.parsiraj(iznos)
                if (c == null) greska = "Unesite iznos." else onSpremi(if (minus) -c else c)
            }) { Text("Spremi", color = Neon.Zelena, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onZatvori) { Text("Odustani", color = Neon.TekstSiv) } },
    )
}

/** Nova ili postojeća rezervacija: iznos i napomena; postojeću se može osloboditi ili obrisati. */
@Composable
private fun RezervacijaDijalog(
    postojeca: Rezervacija?,
    onSpremi: (Rezervacija) -> Unit,
    onOslobodi: (() -> Unit)?,
    onObrisi: (() -> Unit)?,
    onZatvori: () -> Unit,
) {
    var iznos by remember { mutableStateOf(postojeca?.let { Novac.broj(it.iznosCenti) } ?: "") }
    var napomena by remember { mutableStateOf(postojeca?.napomena ?: "") }
    var greska by remember { mutableStateOf<String?>(null) }
    var brisem by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        title = { Text(if (postojeca == null) "Rezerviraj iznos" else "Rezervacija", color = Neon.Tekst) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Novac ostaje na tekućem računu, ali se ne smije potrošiti.", color = Neon.TekstSvijetliSiv, fontSize = 13.sp)
                OznakaPolja("Iznos (${Novac.simbol})")
                NeonPolje(iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                OznakaPolja("Napomena")
                NeonPolje(napomena, { napomena = it }, Modifier.fillMaxWidth(), placeholder = "npr. porez, osiguranje, rata")
                greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
                if (onOslobodi != null) {
                    TonalniGumb("Oslobodi (smije se potrošiti)", onOslobodi, Modifier.fillMaxWidth(), ikona = "check", boja = Neon.Zelena, visina = 44.dp)
                }
            }
        },
        confirmButton = {
            TextButton({
                val c = Novac.parsiraj(iznos)
                if (c == null || c <= 0) greska = "Unesite iznos."
                else onSpremi(
                    (postojeca ?: Rezervacija(iznosCenti = c, kreiranMillis = System.currentTimeMillis())).copy(iznosCenti = c, napomena = napomena.trim()),
                )
            }) { Text("Spremi", color = Neon.Zelena, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (onObrisi != null) TextButton({ brisem = true }) { Text("Obriši", color = Neon.Crvena) }
                TextButton(onZatvori) { Text("Odustani", color = Neon.TekstSiv) }
            }
        },
    )
    if (brisem && onObrisi != null) {
        PotvrdaBrisanja("Obrisati rezervaciju?", "Briše se i iz povijesti. Ako je novac samo slobodan za trošenje, odaberite Oslobodi.", { brisem = false }) {
            brisem = false; onObrisi()
        }
    }
}

private val RAZDOBLJA = listOf(30 to "1M", 90 to "3M", 180 to "6M", 365 to "1G")

/** Analitika: kretanje stanja tekućeg računa po danima, s rezerviranim iznosom. */
@Composable
fun TekuciAnalitikaKartica(s: StanjeTekuceg?, tocke: List<TockaRacuna>, dana: Int, onDana: (Int) -> Unit) {
    var odabrani by remember(tocke.size, tocke.firstOrNull()?.datum) { mutableStateOf<Int?>(null) }
    val idx = (odabrani ?: tocke.lastIndex).coerceIn(-1, tocke.lastIndex)
    val t = tocke.getOrNull(idx)
    Kartica {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stanje tekućeg računa", style = Tip.naslovKartice, modifier = Modifier.weight(1f))
            if (t != null) Oznaka(HrDatum.sDanom(t.datum), Neon.TekstSvijetliSiv)
        }
        Segmenti(RAZDOBLJA.map { it.second }, RAZDOBLJA.indexOfFirst { it.first == dana }.coerceAtLeast(0), { onDana(RAZDOBLJA[it].first) })
        if (s == null || tocke.isEmpty()) {
            Text("Odaberite tekući račun u Štednji i unesite njegovo stanje.", style = Tip.malo)
            return@Kartica
        }
        val prva = tocke.first()
        val zadnja = tocke.last()
        val promjena = zadnja.stanje - prva.stanje
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.skrivaStednju()) {
            Legenda("Stanje", Neon.Cijan, Novac.stanje(t?.stanje ?: 0), Modifier.weight(1f), if ((t?.stanje ?: 0) < 0) Neon.Crvena else Neon.Cijan)
            Legenda("Rezervirano", Neon.Jantar, Novac.eur(t?.rezervirano ?: 0), Modifier.weight(1f))
            Legenda("Promjena", if (promjena >= 0) Neon.Zelena else Neon.Crvena, Novac.predznak(promjena), Modifier.weight(1f))
        }
        GrafRacuna(tocke, idx) { odabrani = it }
        val min = tocke.minBy { it.stanje }
        val max = tocke.maxBy { it.stanje }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.skrivaStednju()) {
            Legenda("Najniže · ${HrDatum.kratko(min.datum)}", Neon.Crvena, Novac.stanje(min.stanje), Modifier.weight(1f), Neon.Tekst)
            Legenda("Najviše · ${HrDatum.kratko(max.datum)}", Neon.Zelena, Novac.stanje(max.stanje), Modifier.weight(1f), Neon.Tekst)
            Legenda("Prosjek", Neon.TekstSiv, Novac.stanje(tocke.sumOf { it.stanje } / tocke.size), Modifier.weight(1f), Neon.Tekst)
        }
        val ispod = tocke.count { it.rezervirano > 0 && it.stanje < it.rezervirano }
        Text(
            (if (ispod > 0) "Dana ispod rezerviranog: $ispod. " else "") + "Dodirnite ili povucite po grafu za pojedini dan.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
            color = if (ispod > 0) Neon.Crvena else Neon.TekstSiv,
        )
    }
}

@Composable
private fun Legenda(naziv: String, boja: Color, vrijednost: String, modifier: Modifier = Modifier, bojaVrijednosti: Color = boja) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(boja))
            Text("  $naziv", style = Tip.oznaka, maxLines = 1)
        }
        Text(vrijednost, color = bojaVrijednosti, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tip.iznos, maxLines = 1)
    }
}

@Composable
private fun GrafRacuna(tocke: List<TockaRacuna>, odabrani: Int, onOdabir: (Int) -> Unit) {
    val n = tocke.size
    val vrijednosti = tocke.flatMap { listOf(it.stanje.toDouble(), it.rezervirano.toDouble()) } + 0.0
    val os = Os.izracunaj(vrijednosti.min(), vrijednosti.max())
    val minV = os.min
    val maxV = os.max
    val labelPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; color = Neon.TekstSiv.toArgb() } }
    val opis = "Linijski graf stanja tekućeg računa od ${HrDatum.kratko(tocke.first().datum)} do ${HrDatum.kratko(tocke.last().datum)}"
    Canvas(
        Modifier.fillMaxWidth().height(200.dp).skrivaStednju()
            .semantics { contentDescription = opis }
            .pointerInput(n) {
                val lijevo = 44.dp.toPx()
                fun indeks(x: Float) = (((x - lijevo) / (size.width - lijevo)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                detectTapGestures { onOdabir(indeks(it.x)) }
            }
            .pointerInput(n) {
                val lijevo = 44.dp.toPx()
                fun indeks(x: Float) = (((x - lijevo) / (size.width - lijevo)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                detectDragGestures { change, _ -> onOdabir(indeks(change.position.x)) }
            },
    ) {
        val lijevo = 44.dp.toPx()
        val gore = 10.dp.toPx()
        val dolje = size.height - 22.dp.toPx()
        val w = size.width - lijevo
        fun x(i: Int) = lijevo + w * (i.toFloat() / (n - 1).coerceAtLeast(1))
        fun y(v: Double) = (dolje - (v - minV) / (maxV - minV) * (dolje - gore)).toFloat()
        labelPaint.textSize = 11.sp.toPx()
        for (v in os.oznake) {
            val yy = y(v)
            drawLine(if (v == 0.0) Neon.Crvena.copy(alpha = .6f) else Color(0x12FFFFFF), Offset(lijevo, yy), Offset(size.width, yy), 1f)
            drawIntoCanvas {
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                it.nativeCanvas.drawText(Novac.cijeli((v / 100).roundToInt().toLong()), lijevo - 6.dp.toPx(), yy + 4.dp.toPx(), labelPaint)
            }
        }
        drawIntoCanvas {
            val c = it.nativeCanvas
            labelPaint.textAlign = android.graphics.Paint.Align.LEFT
            c.drawText(HrDatum.kratko(tocke.first().datum), lijevo, size.height - 4.dp.toPx(), labelPaint)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            c.drawText(HrDatum.kratko(tocke.last().datum), size.width, size.height - 4.dp.toPx(), labelPaint)
        }
        // Dani ispod rezerviranog iznosa: crvena traka preko cijele visine
        tocke.forEachIndexed { i, t ->
            if (t.rezervirano > 0 && t.stanje < t.rezervirano) {
                val sirina = w / (n - 1).coerceAtLeast(1)
                drawRect(Neon.Crvena.copy(alpha = .14f), Offset(x(i) - sirina / 2, gore), androidx.compose.ui.geometry.Size(sirina, dolje - gore))
            }
        }
        // Rezervirano: isprekidana stepenasta linija
        if (tocke.any { it.rezervirano > 0 }) {
            val pr = Path().apply {
                tocke.forEachIndexed { i, t ->
                    if (i == 0) moveTo(x(i), y(t.rezervirano.toDouble())) else { lineTo(x(i), y(tocke[i - 1].rezervirano.toDouble())); lineTo(x(i), y(t.rezervirano.toDouble())) }
                }
            }
            drawPath(pr, Neon.Jantar, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }
        val ps = Path().apply { tocke.forEachIndexed { i, t -> if (i == 0) moveTo(x(i), y(t.stanje.toDouble())) else lineTo(x(i), y(t.stanje.toDouble())) } }
        val ispuna = Path().apply {
            addPath(ps); lineTo(x(n - 1), y(0.0.coerceAtLeast(minV))); lineTo(x(0), y(0.0.coerceAtLeast(minV))); close()
        }
        drawPath(ispuna, Brush.verticalGradient(listOf(Neon.Cijan.copy(alpha = .28f), Color.Transparent), gore, dolje))
        drawPath(ps, Neon.Cijan.copy(alpha = .25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(ps, Neon.Cijan, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        tocke.getOrNull(odabrani)?.let { t ->
            val xo = x(odabrani)
            drawLine(Color(0x73FFFFFF), Offset(xo, gore), Offset(xo, dolje), 1f)
            drawCircle(Neon.Pozadina, 5.dp.toPx(), Offset(xo, y(t.stanje.toDouble())))
            drawCircle(Neon.Cijan, 5.dp.toPx(), Offset(xo, y(t.stanje.toDouble())), style = Stroke(2.dp.toPx()))
        }
    }
}
