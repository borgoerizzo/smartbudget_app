package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.GodisnjiPregled
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.Os
import hr.smartbudget.app.domain.StatusLimita
import hr.smartbudget.app.domain.Zbroj
import hr.smartbudget.app.ui.AnalitikaStanje
import hr.smartbudget.app.ui.BiracCiklusa
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGraf
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.PopisStavkiKategorije
import hr.smartbudget.app.data.TipTransakcije
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.stilStatusa
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnalitikaEkran(
    s: AnalitikaStanje?,
    onPrethodni: () -> Unit,
    onSljedeci: () -> Unit,
    onTrenutni: () -> Unit,
    onBrojCiklusa: (Int) -> Unit,
    godisnji: GodisnjiPregled?,
    onGodina: (Int) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Analitika", style = Tip.naslovVeliki)
                if (s != null) BiracCiklusa(HrDatum.raspon(s.ciklus), onPrethodni, onSljedeci, onTrenutni, Modifier.fillMaxWidth())
            }
        }
        if (s == null) return@LazyColumn
        item { TijekNovcaKartica(s) }
        item { RaspodjelaKartica(s) }
        item { UsporedbaKartica(s) }
        item { PovijestKartica(s, onBrojCiklusa) }
        if (godisnji != null) item { GodisnjiKartica(godisnji, onGodina) }
    }
}

private val bojaNeto = Neon.LjubicastaSvijetla

@Composable
private fun Legenda(naziv: String, boja: Color, vrijednost: String, modifier: Modifier = Modifier, bojaVrijednosti: Color = boja) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(boja))
            Spacer(Modifier.width(6.dp))
            Text(naziv, style = Tip.oznaka)
        }
        Text(vrijednost, color = bojaVrijednosti, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tip.iznos, maxLines = 1)
    }
}

@Composable
private fun TijekNovcaKartica(s: AnalitikaStanje) {
    var odabrani by remember(s.ciklus, s.tijek.size) { mutableStateOf<Int?>(null) }
    val tocke = s.tijek
    val idx = (odabrani ?: tocke.lastIndex).coerceIn(-1, tocke.lastIndex)
    val t = tocke.getOrNull(idx)

    Kartica {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Kumulativni tok novca", style = Tip.naslovKartice, modifier = Modifier.weight(1f))
            if (t != null) Oznaka(HrDatum.sDanom(t.datum), Neon.TekstSvijetliSiv)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Legenda("Prihodi", Neon.Zelena, Novac.eur(t?.prihodi ?: 0), Modifier.weight(1f))
            Legenda("Rashodi", Neon.Crvena, Novac.eur(t?.troskovi ?: 0), Modifier.weight(1f))
            Legenda("Neto", bojaNeto, Novac.stanje(t?.neto ?: 0), Modifier.weight(1f), Neon.LjubicastaTekst)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Neon.Cijan))
            Text("  Stanje računa: ", style = Tip.oznaka)
            Text(Novac.stanje(t?.stanje ?: 0), color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Tip.iznos)
        }
        if (tocke.isEmpty()) {
            Text("Ciklus još nije počeo.", style = Tip.malo)
        } else {
            GrafTijeka(s, idx) { odabrani = it }
            Text(
                if (s.jeAktivni) "Isprekidano: procjena do kraja ciklusa uz trenutni tempo. Dodirnite ili povucite po grafu za pojedini dan."
                else "Dodirnite ili povucite po grafu za pojedini dan.",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
            )
        }
    }
}

@Composable
private fun GrafTijeka(s: AnalitikaStanje, odabrani: Int, onOdabir: (Int) -> Unit) {
    val tocke = s.tijek
    val n = s.ciklus.brojDana
    val zadnja = tocke.last()
    // Projekcija rashoda do kraja ciklusa uz trenutni dnevni tempo.
    val tempo = if (tocke.isNotEmpty()) zadnja.troskovi.toDouble() / tocke.size else 0.0
    val projRashodi = zadnja.troskovi + tempo * (n - tocke.size)
    val vrijednosti = tocke.flatMap { listOf(it.prihodi, it.troskovi, it.neto, it.stanje) }.map { it.toDouble() } +
        (if (s.jeAktivni) listOf(projRashodi) else emptyList()) + 0.0
    // Dinamična os: minus dobiva vidljiv dio visine bez obzira na veličinu prihoda.
    val os = Os.izracunaj(vrijednosti.min(), vrijednosti.max())
    val minV = os.min
    val maxV = os.max
    val opis = "Linijski graf kumulativnih prihoda, rashoda, neto toka i stanja računa za ciklus ${HrDatum.raspon(s.ciklus)}"
    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; color = Neon.TekstSiv.toArgb() }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .semantics { contentDescription = opis }
            .pointerInput(tocke.size, n) {
                val lijevo = 44.dp.toPx()
                fun indeks(x: Float) = (((x - lijevo) / (size.width - lijevo)) * (n - 1)).roundToInt().coerceIn(0, tocke.lastIndex)
                detectTapGestures { onOdabir(indeks(it.x)) }
            }
            .pointerInput(tocke.size, n) {
                val lijevo = 44.dp.toPx()
                fun indeks(x: Float) = (((x - lijevo) / (size.width - lijevo)) * (n - 1)).roundToInt().coerceIn(0, tocke.lastIndex)
                detectDragGestures { change, _ -> onOdabir(indeks(change.position.x)) }
            },
    ) {
        val lijevo = 44.dp.toPx()
        val gore = 16.dp.toPx()
        val dolje = size.height - 22.dp.toPx()
        val w = size.width - lijevo
        fun x(i: Number) = lijevo + w * (i.toFloat() / (n - 1).coerceAtLeast(1))
        fun y(v: Double) = (dolje - (v - minV) / (maxV - minV) * (dolje - gore)).toFloat()
        labelPaint.textSize = 11.sp.toPx()

        // Područje ispod nule blago crveno
        if (minV < 0) drawRect(Neon.Crvena.copy(alpha = .06f), Offset(lijevo, y(0.0)), androidx.compose.ui.geometry.Size(w, y(minV) - y(0.0)))
        // Mreža i oznake osi Y (0 se crta posebno, crveno)
        for (v in os.oznake.filter { it != 0.0 }) {
            val yy = y(v)
            drawLine(Color(0x12FFFFFF), Offset(lijevo, yy), Offset(size.width, yy), 1f)
            drawIntoCanvas {
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                it.nativeCanvas.drawText(Novac.cijeli((v / 100).roundToInt().toLong()), lijevo - 6.dp.toPx(), yy + 4.dp.toPx(), labelPaint)
            }
        }
        // Nula: crvena isprekidana linija, da se jasno vidi kad stanje prijeđe u minus.
        drawLine(
            Neon.Crvena.copy(alpha = .75f), Offset(lijevo, y(0.0)), Offset(size.width, y(0.0)), 1.4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        drawIntoCanvas {
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            labelPaint.color = Neon.Crvena.toArgb()
            it.nativeCanvas.drawText("0 €", size.width, y(0.0) - 4.dp.toPx(), labelPaint)
            labelPaint.color = Neon.TekstSiv.toArgb()
        }
        // Oznake osi X: početak, sredina, kraj
        drawIntoCanvas {
            val c = it.nativeCanvas
            labelPaint.textAlign = android.graphics.Paint.Align.LEFT
            c.drawText(HrDatum.kratko(s.ciklus.pocetak), lijevo, size.height - 4.dp.toPx(), labelPaint)
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            c.drawText(HrDatum.kratko(s.ciklus.pocetak.plusDays((n / 2).toLong())), x(n / 2), size.height - 4.dp.toPx(), labelPaint)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            c.drawText(HrDatum.kratko(s.ciklus.kraj), size.width, size.height - 4.dp.toPx(), labelPaint)
        }

        fun putanja(vr: (Int) -> Double): Path = Path().apply {
            tocke.indices.forEach { i -> if (i == 0) moveTo(x(i), y(vr(i))) else lineTo(x(i), y(vr(i))) }
        }
        fun ispuna(p: Path, boja: Color, alfa: Float) {
            val f = Path().apply {
                addPath(p)
                lineTo(x(tocke.lastIndex), y(0.0.coerceAtLeast(minV)))
                lineTo(x(0), y(0.0.coerceAtLeast(minV)))
                close()
            }
            drawPath(f, Brush.verticalGradient(listOf(boja.copy(alpha = alfa), Color.Transparent), gore, dolje))
        }
        val pPrihodi = putanja { tocke[it].prihodi.toDouble() }
        val pRashodi = putanja { tocke[it].troskovi.toDouble() }
        val pNeto = putanja { tocke[it].neto.toDouble() }
        val pStanje = putanja { tocke[it].stanje.toDouble() }
        ispuna(pPrihodi, Neon.Zelena, .16f)
        ispuna(pRashodi, Neon.Crvena, .20f)
        ispuna(pNeto, bojaNeto, .38f)

        if (s.jeAktivni && tocke.size < n) {
            val crt = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
            drawLine(Neon.Zelena.copy(alpha = .6f), Offset(x(tocke.lastIndex), y(zadnja.prihodi.toDouble())), Offset(x(n - 1), y(zadnja.prihodi.toDouble())), 1.6.dp.toPx(), StrokeCap.Round, crt)
            drawLine(Neon.Crvena.copy(alpha = .85f), Offset(x(tocke.lastIndex), y(zadnja.troskovi.toDouble())), Offset(x(n - 1), y(projRashodi)), 1.6.dp.toPx(), StrokeCap.Round, crt)
        }
        linijaSaSjajem(pStanje, Neon.Cijan.copy(alpha = .8f), 1.6.dp.toPx())
        linijaSaSjajem(pPrihodi, Neon.Zelena, 2.2.dp.toPx())
        linijaSaSjajem(pRashodi, Neon.Crvena, 2.2.dp.toPx())
        linijaSaSjajem(pNeto, bojaNeto, 2.8.dp.toPx())

        if (s.jeAktivni) {
            val xd = x(tocke.lastIndex)
            drawLine(Color(0x47FFFFFF), Offset(xd, gore), Offset(xd, dolje), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawIntoCanvas {
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                labelPaint.color = Neon.Tekst.toArgb()
                it.nativeCanvas.drawText("Danas", xd.coerceIn(lijevo + 20.dp.toPx(), size.width - 20.dp.toPx()), gore - 4.dp.toPx(), labelPaint)
                labelPaint.color = Neon.TekstSiv.toArgb()
            }
        }
        // Odabrani dan
        tocke.getOrNull(odabrani)?.let { t ->
            val xo = x(odabrani)
            drawLine(Color(0x73FFFFFF), Offset(xo, gore), Offset(xo, dolje), 1f)
            listOf(t.prihodi to Neon.Zelena, t.troskovi to Neon.Crvena, t.neto to bojaNeto, t.stanje to Neon.Cijan).forEach { (v, c) ->
                drawCircle(Neon.Pozadina, 5.dp.toPx(), Offset(xo, y(v.toDouble())))
                drawCircle(c, 5.dp.toPx(), Offset(xo, y(v.toDouble())), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.linijaSaSjajem(p: Path, boja: Color, debljina: Float) {
    drawPath(p, boja.copy(alpha = .25f), style = Stroke(debljina * 3, cap = StrokeCap.Round))
    drawPath(p, boja, style = Stroke(debljina, cap = StrokeCap.Round))
}

@Composable
private fun RaspodjelaKartica(s: AnalitikaStanje) {
    val r = s.raspodjela
    Kartica(razmak = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Rashodi po kategorijama", style = Tip.naslovKartice, modifier = Modifier.weight(1f))
            Text(
                when (r.size) { 1 -> "1 kategorija"; in 2..4 -> "${r.size} kategorije"; else -> "${r.size} kategorija" },
                style = Tip.oznaka,
            )
        }
        if (r.isEmpty()) {
            Text("U ovom ciklusu nema rashoda.", style = Tip.malo)
            return@Kartica
        }
        KruzniGraf(r, "Ukupno rashoda", s.zbroj.troskovi, Modifier.align(Alignment.CenterHorizontally))
        // Dodir na kategoriju širi popis njezinih stavki.
        var rasirene by remember(s.ciklus) { mutableStateOf(setOf<Long>()) }
        fun prebaci(id: Long) { rasirene = if (id in rasirene) rasirene - id else rasirene + id }
        fun stavke(ids: Set<Long>) = s.transakcije.filter { it.kategorija.id in ids && it.transakcija.tip == TipTransakcije.TROSAK }
        Column {
            val prikaz = if (r.size > 6) r.take(5) else r
            val ostalo = if (r.size > 6) r.drop(5) else emptyList()
            prikaz.forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider(color = Color(0x12FFFFFF))
                val id = u.kategorija.id
                Box(Modifier.clickable(onClickLabel = "Prikaži stavke") { prebaci(id) }) {
                    RedakLegende(u.kategorija.naziv, hexBoja(u.kategorija.bojaHex), u.centi, u.udio, u.iskoristenost, u.status, rasireno = id in rasirene)
                }
                if (id in rasirene) PopisStavkiKategorije(stavke(setOf(id)))
            }
            if (ostalo.isNotEmpty()) {
                HorizontalDivider(color = Color(0x12FFFFFF))
                val c = ostalo.sumOf { it.centi }
                val maxIsk = ostalo.mapNotNull { it.iskoristenost }.maxOrNull()
                Box(Modifier.clickable(onClickLabel = "Prikaži stavke") { prebaci(-1L) }) {
                    RedakLegende(
                        "Ostalo (${ostalo.size} kategorije)", Neon.TekstSiv, c, ostalo.sumOf { it.udio }, maxIsk,
                        ostalo.maxOfOrNull { it.status } ?: StatusLimita.BEZ_LIMITA, najvise = true, rasireno = -1L in rasirene,
                    )
                }
                if (-1L in rasirene) PopisStavkiKategorije(stavke(ostalo.map { it.kategorija.id }.toSet()))
            }
        }
    }
}

@Composable
private fun RedakLegende(
    naziv: String,
    boja: Color,
    centi: Long,
    udio: Double,
    iskoristenost: Double?,
    status: StatusLimita,
    najvise: Boolean = false,
    rasireno: Boolean = false,
) {
    val st = stilStatusa(status)
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(boja))
            Text(naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text(Novac.eur(centi), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst)
            Icon(Ikone.ui(if (rasireno) "chevUp" else "chevDown"), null, Modifier.padding(start = 6.dp).size(16.dp), tint = Neon.TekstSiv)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Novac.postotak(udio)} rashoda", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
            if (iskoristenost != null) {
                Box(Modifier.width(76.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x17FFFFFF))) {
                    Box(Modifier.fillMaxWidth((iskoristenost / 100).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(st.boja))
                }
                Text(
                    (if (najvise) "najviše " else "") + "${iskoristenost.roundToInt()} % limita",
                    color = st.boja, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                Text("bez limita", style = Tip.oznaka)
            }
        }
    }
}

@Composable
private fun UsporedbaKartica(s: AnalitikaStanje) {
    Kartica(razmak = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Usporedba s prošlim ciklusom", style = Tip.naslovKartice)
            Text("Do istog dana ciklusa (dan ${s.danUCiklusu})", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlocicaUsporedbe("Prihodi", s.sadaDoDana.prihodi, s.prijeDoDana.prihodi, rastJeDobar = true, Modifier.weight(1f))
            PlocicaUsporedbe("Rashodi", s.sadaDoDana.troskovi, s.prijeDoDana.troskovi, rastJeDobar = false, Modifier.weight(1f))
            PlocicaUsporedbe("Neto", s.sadaDoDana.razlika, s.prijeDoDana.razlika, rastJeDobar = true, Modifier.weight(1f))
        }
        if (s.promjene.isNotEmpty()) {
            Column {
                Text("Najveće promjene po kategorijama", style = Tip.tijelo.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 4.dp))
                s.promjene.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = Color(0x12FFFFFF))
                    val maks = maxOf(m.prije, m.sada).coerceAtLeast(1)
                    val boja = hexBoja(m.kategorija.bojaHex)
                    val p = m.promjenaPosto
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row {
                            Text(m.kategorija.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Text(
                                when { p == null -> "novo"; p >= 0 -> "▲ ${Novac.postotak(p)}"; else -> "▼ ${Novac.postotak(abs(p))}" },
                                color = if (m.sada > m.prije) Neon.Crvena else Neon.Zelena, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        TrakaUsporedbe(m.prije.toFloat() / maks, Color(0xFF5B6778), Novac.eur(m.prije), false)
                        TrakaUsporedbe(m.sada.toFloat() / maks, boja, Novac.eur(m.sada), true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrakaUsporedbe(udio: Float, boja: Color, tekst: String, istaknuto: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x0FFFFFFF))) {
            if (udio > 0) Box(Modifier.fillMaxWidth(udio.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(boja))
        }
        Text(
            tekst, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = if (istaknuto) Neon.Tekst else Neon.TekstSiv, fontSize = 12.sp,
            fontWeight = if (istaknuto) FontWeight.SemiBold else FontWeight.Normal, style = Tip.iznos,
        )
    }
}

@Composable
private fun PlocicaUsporedbe(naziv: String, sada: Long, prije: Long, rastJeDobar: Boolean, modifier: Modifier) {
    val promjena = if (prije != 0L) (sada - prije) * 100.0 / abs(prije) else null
    val dobro = if (rastJeDobar) sada >= prije else sada <= prije
    val boja = if (dobro) Neon.Zelena else Neon.Crvena
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Neon.Ispuna)
            .border(1.dp, Neon.RubSlab, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(naziv, style = Tip.oznaka)
        Text(Novac.stanje(sada), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, maxLines = 1)
        Oznaka(
            when {
                promjena == null -> "—"
                promjena >= 0 -> "▲ ${Novac.postotak(promjena)}"
                else -> "▼ ${Novac.postotak(-promjena)}"
            },
            if (promjena == null) Neon.TekstSiv else boja,
        )
        Text("prošli ${Novac.broj(prije)}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
    }
}

@Composable
private fun PovijestKartica(s: AnalitikaStanje, onBrojCiklusa: (Int) -> Unit) {
    Kartica(razmak = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Prihodi i rashodi kroz vrijeme", style = Tip.naslovKartice)
            Text("Zadnjih ${s.brojCiklusa} ciklusa, zaključno s odabranim", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        Segmenti(listOf("6 ciklusa", "12 ciklusa"), if (s.brojCiklusa == 6) 0 else 1, { onBrojCiklusa(if (it == 0) 6 else 12) })
        val p = s.povijest
        val maks = p.maxOfOrNull { maxOf(it.zbroj.prihodi, it.zbroj.troskovi) }?.coerceAtLeast(1) ?: 1
        val labelPaint = remember { android.graphics.Paint().apply { isAntiAlias = true; color = Neon.TekstSiv.toArgb() } }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = "Stupčasti graf prihoda i rashoda po ciklusima" },
        ) {
            val dolje = size.height - 20.dp.toPx()
            val gore = 6.dp.toPx()
            val sirina = size.width / p.size
            val stup = (sirina * .32f).coerceAtMost(14.dp.toPx())
            labelPaint.textSize = 10.sp.toPx()
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawLine(Color(0x17FFFFFF), Offset(0f, dolje), Offset(size.width, dolje), 1f)
            p.forEachIndexed { i, st ->
                val cx = sirina * i + sirina / 2
                fun h(v: Long) = (dolje - gore) * v / maks
                val hp = h(st.zbroj.prihodi)
                val hr = h(st.zbroj.troskovi)
                val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                drawRoundRect(Neon.Zelena, Offset(cx - stup - 1.dp.toPx(), dolje - hp), Size(stup, hp), r)
                drawRoundRect(Neon.Crvena, Offset(cx + 1.dp.toPx(), dolje - hr), Size(stup, hr), r)
                drawIntoCanvas {
                    labelPaint.color = (if (i == p.lastIndex) Neon.Tekst else Neon.TekstSiv).toArgb()
                    it.nativeCanvas.drawText(HrDatum.mjeseciKratko[st.ciklus.pocetak.monthValue - 1], cx, size.height - 4.dp.toPx(), labelPaint)
                }
            }
        }
        // Prosjek samo iz ciklusa koji imaju unose: prazni (prije početka korištenja) bi ga lažno smanjili.
        val sUnosima = p.filter { it.zbroj.prihodi > 0 || it.zbroj.troskovi > 0 }
        val n = sUnosima.size.coerceAtLeast(1)
        val prosjek = Zbroj(sUnosima.sumOf { it.zbroj.prihodi } / n, sUnosima.sumOf { it.zbroj.troskovi } / n)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Legenda("Prosj. prihodi", Neon.Zelena, Novac.eur(prosjek.prihodi), Modifier.weight(1f))
            Legenda("Prosj. rashodi", Neon.Crvena, Novac.eur(prosjek.troskovi), Modifier.weight(1f))
            Legenda("Prosj. neto", bojaNeto, Novac.stanje(prosjek.razlika), Modifier.weight(1f), Neon.LjubicastaTekst)
        }
        Text(
            when (sUnosima.size) {
                0 -> "Još nema unosa u ovom razdoblju."
                1 -> "Prosjek iz 1 ciklusa s unosima."
                else -> "Prosjek iz ${sUnosima.size} ciklusa s unosima (od ${p.size} prikazanih)."
            },
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
        )
    }
}
