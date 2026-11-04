package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.brojDana
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.domain.fiksniPromjenjivi
import hr.smartbudget.app.domain.jeStvarna
import hr.smartbudget.app.domain.kalendarPotrosnje
import hr.smartbudget.app.domain.najcesciNazivi
import hr.smartbudget.app.domain.prosjekPoDanuUTjednu
import hr.smartbudget.app.domain.stopaStednje
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.ui.AnalitikaStanje
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PopisStavkiKategorije
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.LocalDate
import kotlin.math.sqrt

private val daniZaglavlje = listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned")
private val daniUTjednu = listOf("ponedjeljkom", "utorkom", "srijedom", "četvrtkom", "petkom", "subotom", "nedjeljom")

@Composable
private fun Naslov(naslov: String, podnaslov: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(naslov, style = Tip.naslovKartice)
        Text(podnaslov, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
    }
}

// --- 1. Kalendar potrošnje ---

@Composable
fun KalendarKartica(s: AnalitikaStanje) {
    val dani = remember(s.ciklus, s.transakcije) { kalendarPotrosnje(s.ciklus, s.transakcije) }
    val maks = dani.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val danas = LocalDate.now()
    var odabrani by remember(s.ciklus) { mutableStateOf<LocalDate?>(null) }

    Kartica(razmak = 12.dp) {
        Naslov("Kalendar potrošnje", "Što je dan crveniji, to je tog dana potrošeno više. Dodirni dan.")
        Row(Modifier.fillMaxWidth()) {
            daniZaglavlje.forEach { Text(it, style = Tip.oznaka, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        // Prazna polja do dana u tjednu kojim ciklus počinje, pa svi dani ciklusa
        val pomak = s.ciklus.pocetak.dayOfWeek.value - 1
        val polja: List<Pair<LocalDate, Long>?> = List(pomak) { null } + dani
        polja.chunked(7).forEach { tjedan ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { i ->
                    val polje = tjedan.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (polje != null) {
                            val (d, v) = polje
                            val jacina = if (v > 0) (0.18f + 0.82f * sqrt(v.toFloat() / maks)) else 0f
                            val oblik = RoundedCornerShape(8.dp)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .then(if (jacina > 0.7f) Modifier.neonSjaj(Neon.Crvena, 8.dp, 8.dp, .35f) else Modifier)
                                    .clip(oblik)
                                    .background(if (v > 0) Neon.Crvena.copy(alpha = jacina) else Color(0x0AFFFFFF))
                                    .border(
                                        if (d == odabrani || d == danas) 1.5.dp else 0.dp,
                                        when { d == odabrani -> Neon.Tekst; d == danas -> Neon.Cijan; else -> Color.Transparent },
                                        oblik,
                                    )
                                    .clickable(onClickLabel = "Prikaži dan") { odabrani = if (odabrani == d) null else d },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${d.dayOfMonth}",
                                    color = if (d.isAfter(danas)) Neon.Neaktivno else Neon.Tekst,
                                    fontSize = 12.sp, fontWeight = if (v > 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Legenda intenziteta
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("manje", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
            listOf(0f, .25f, .5f, .75f, 1f).forEach { a ->
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(if (a == 0f) Color(0x0AFFFFFF) else Neon.Crvena.copy(alpha = 0.18f + 0.82f * a)))
            }
            Text("više", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
            Text(
                "  · najviše ${Novac.eur(maks)}",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
            )
        }
        odabrani?.let { d ->
            val stavke = s.transakcije.filter { it.jeStvarna && it.transakcija.tip == TipTransakcije.TROSAK && it.datum() == d }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(HrDatum.sDanom(d), style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text(Novac.eur(stavke.sumOf { it.transakcija.iznosCenti }), color = Neon.Crvena, style = Tip.iznos)
            }
            if (stavke.isEmpty()) Text("Tog dana nema rashoda.", style = Tip.malo) else PopisStavkiKategorije(stavke, Modifier.padding(start = 0.dp))
        }
    }
}

// --- 2. Navike po danima u tjednu ---

@Composable
fun NavikeKartica(s: AnalitikaStanje) {
    val prosjeci = remember(s.transakcijeRazdoblja, s.razdobljeOd, s.razdobljeDo) {
        prosjekPoDanuUTjednu(s.transakcijeRazdoblja, s.razdobljeOd, s.razdobljeDo)
    }
    val maks = prosjeci.max().coerceAtLeast(1)
    val iMaks = prosjeci.indexOf(prosjeci.max())
    val iMin = prosjeci.indexOf(prosjeci.min())
    Kartica(razmak = 14.dp) {
        Naslov("Navike po danima u tjednu", "Prosječni rashod po danu · zadnjih ${brojDana(s.razdobljeOd, s.razdobljeDo)} dana")
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            prosjeci.forEachIndexed { i, v ->
                val boja = if (i == iMaks && v > 0) Neon.Jantar else Neon.Cijan
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(if (v > 0) Novac.cijeli((v + 50) / 100) else "", color = if (i == iMaks) Neon.Jantar else Neon.TekstSiv, fontSize = 10.sp, maxLines = 1)
                    val udio = (v.toFloat() / maks).coerceIn(0.03f, 1f)
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(udio * 0.78f)
                            .then(if (i == iMaks && v > 0) Modifier.neonSjaj(Neon.Jantar, 10.dp, 8.dp, .4f) else Modifier)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(boja, boja.copy(alpha = .45f)))),
                    )
                    Text(daniZaglavlje[i], style = Tip.oznaka, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (prosjeci.max() > 0) {
            val omjer = if (prosjeci[iMin] > 0) prosjeci[iMaks].toDouble() / prosjeci[iMin] else null
            Text(
                "Najviše trošiš ${daniUTjednu[iMaks]} (prosječno ${Novac.eur(prosjeci[iMaks])}), najmanje ${daniUTjednu[iMin]}" +
                    (omjer?.let { " · ${"%.1f".format(it).replace('.', ',')}× razlike" } ?: " (često bez troška)") + ".",
                color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
            )
        } else {
            Text("Još nema dovoljno rashoda za usporedbu dana.", style = Tip.malo)
        }
    }
}

// --- 3. Fiksni i promjenjivi troškovi ---

@Composable
fun FiksniKartica(s: AnalitikaStanje) {
    val f = remember(s.transakcije) { fiksniPromjenjivi(s.transakcije) }
    Kartica(razmak = 14.dp) {
        Naslov("Fiksni i promjenjivi troškovi", "Fiksni = ponavljajući unosi (krediti, pretplate, režije) · ovaj ciklus")
        if (f.ukupno == 0L) {
            Text("U ovom ciklusu nema rashoda.", style = Tip.malo)
            return@Kartica
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp).semantics { contentDescription = "Fiksni ${f.udioFiksnih.toInt()} posto rashoda" }) {
                    val deb = 16.dp.toPx()
                    val r = size.minDimension / 2 - deb
                    val tl = Offset(center.x - r, center.y - r)
                    val sz = Size(r * 2, r * 2)
                    val kut = (f.udioFiksnih * 3.6).toFloat()
                    drawArc(Neon.Cijan.copy(alpha = .85f), -90f + kut, 360f - kut, false, tl, sz, style = Stroke(deb))
                    drawArc(Neon.LjubicastaSvijetla.copy(alpha = .3f), -90f, kut, false, tl, sz, style = Stroke(deb + 8.dp.toPx()))
                    drawArc(Neon.LjubicastaSvijetla, -90f, kut, false, tl, sz, style = Stroke(deb))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${f.udioFiksnih.toInt()} %", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Neon.LjubicastaTekst)
                    Text("fiksno", style = Tip.oznaka)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Legenda("Fiksni", Novac.eur(f.fiksni), Neon.LjubicastaSvijetla)
                Legenda("Promjenjivi", Novac.eur(f.promjenjivi), Neon.Cijan)
            }
        }
        Text(
            if (f.fiksni > 0) "Na promjenjive troškove (hrana, izlasci, kupnje…) otpada ${Novac.eur(f.promjenjivi)}: to je dio na koji najlakše utječeš."
            else "Nijedan rashod nije iz ponavljajućih unosa. Kredite, pretplate i režije dodaj u Budžeti → Ponavljajući unosi.",
            color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (f.najveciFiksni.isNotEmpty()) {
            Column {
                f.najveciFiksni.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.transakcija.opis.ifBlank { t.kategorija.naziv }, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Novac.eur(t.transakcija.iznosCenti), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.LjubicastaTekst)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legenda(naziv: String, iznos: String, boja: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(boja))
            Text("  $naziv", style = Tip.oznaka)
        }
        Text(iznos, style = Tip.iznos.copy(fontSize = 17.sp), color = Neon.Tekst)
    }
}

// --- 4. Stopa štednje ---

@Composable
fun StednjaKartica(s: AnalitikaStanje) {
    val stopa = stopaStednje(s.zbroj)
    val boja = when {
        stopa == null -> Neon.TekstSiv
        stopa < 0 -> Neon.Crvena
        stopa < 10 -> Neon.Jantar
        else -> Neon.Zelena
    }
    Kartica(razmak = 14.dp) {
        Naslov("Stopa štednje", "Koliki dio prihoda ciklusa je ostao")
        Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.BottomCenter) {
            // Polukružna kazaljka od −50 % do 100 %
            Canvas(Modifier.size(width = 220.dp, height = 120.dp).semantics { contentDescription = "Stopa štednje ${stopa?.toInt() ?: 0} posto" }) {
                val deb = 16.dp.toPx()
                val r = size.width / 2 - deb
                val tl = Offset(center.x - r, size.height - r - deb / 2)
                val sz = Size(r * 2, r * 2)
                drawArc(Color(0x14FFFFFF), 180f, 180f, false, tl, sz, style = Stroke(deb, cap = StrokeCap.Round))
                val udio = (((stopa ?: 0.0) + 50) / 150).coerceIn(0.0, 1.0).toFloat()
                if (stopa != null) {
                    drawArc(boja.copy(alpha = .3f), 180f, 180f * udio, false, tl, sz, style = Stroke(deb + 8.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(boja, 180f, 180f * udio, false, tl, sz, style = Stroke(deb, cap = StrokeCap.Round))
                }
                // Oznaka nule (1/3 luka)
                val kut = Math.toRadians(180.0 + 60.0)
                val c = Offset(tl.x + r, tl.y + r)
                drawLine(
                    Neon.Tekst.copy(alpha = .6f),
                    Offset(c.x + ((r - deb) * Math.cos(kut)).toFloat(), c.y + ((r - deb) * Math.sin(kut)).toFloat()),
                    Offset(c.x + ((r + deb) * Math.cos(kut)).toFloat(), c.y + ((r + deb) * Math.sin(kut)).toFloat()),
                    2.dp.toPx(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stopa?.let { "${"%.0f".format(it)} %" } ?: "—", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = boja)
                Text(
                    when {
                        stopa == null -> "nema prihoda u ciklusu"
                        stopa < 0 -> "potrošeno više od prihoda"
                        else -> "prihoda je ostalo"
                    },
                    style = Tip.oznaka,
                )
            }
        }
        // Trend kroz zadnjih 6 ciklusa
        val trend = s.povijest.takeLast(6)
        Text("Zadnjih ${trend.size} ciklusa", style = Tip.oznaka)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            trend.forEach { p ->
                val st = stopaStednje(p.zbroj)
                val b = when { st == null -> Neon.Neaktivno; st < 0 -> Neon.Crvena; st < 10 -> Neon.Jantar; else -> Neon.Zelena }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(b.copy(alpha = .16f))
                            .border(1.dp, b.copy(alpha = .45f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(st?.let { "${"%.0f".format(it)}%" } ?: "—", color = b, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Text(HrDatum.mjeseciKratko[p.ciklus.pocetak.monthValue - 1], style = Tip.oznaka.copy(fontSize = 11.sp))
                }
            }
        }
        Text("Dobro pravilo: odvoji barem 10–20 % prihoda.", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
    }
}

// --- 5. Gdje najčešće trošiš ---

@Composable
fun NaziviKartica(s: AnalitikaStanje) {
    val nazivi = remember(s.transakcijeRazdoblja) { najcesciNazivi(s.transakcijeRazdoblja) }
    Kartica(razmak = 12.dp) {
        Naslov("Gdje najčešće trošiš", "Po broju transakcija · zadnjih ${brojDana(s.razdobljeOd, s.razdobljeDo)} dana")
        if (nazivi.isEmpty()) {
            Text("Unesi naziv uz troškove (npr. Spar, Lidl) da se ovdje pojave najčešća mjesta.", style = Tip.malo)
            return@Kartica
        }
        val maks = nazivi.maxOf { it.ukupno }.coerceAtLeast(1)
        nazivi.forEachIndexed { i, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (i == 0) Neon.Jantar.copy(alpha = .2f) else Color(0x0FFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = if (i == 0) Neon.Jantar else Neon.TekstSvijetliSiv, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Novac.eur(n.ukupno), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst)
                    }
                    TrakaNapretka(n.ukupno.toFloat() / maks, Brush.horizontalGradient(listOf(Neon.Cijan, Neon.LjubicastaSvijetla)), Neon.Cijan, visina = 5.dp)
                    Text("${n.broj}× · prosječno ${Novac.eur(n.prosjek)} po transakciji", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
                }
            }
        }
    }
}
