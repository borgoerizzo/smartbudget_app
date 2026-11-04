package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.GodisnjiPregled
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.ui.BiracCiklusa
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja

/** Godišnji pregled: mjeseci, ukupno, prosjek, najveći troškovi i kategorije za kalendarsku godinu. */
@Composable
fun GodisnjiKartica(g: GodisnjiPregled, onGodina: (Int) -> Unit, zaglavlje: @Composable () -> Unit) {
    Kartica(razmak = 14.dp) {
        zaglavlje()
        BiracCiklusa(
            "Godina ${g.godina}.",
            onPrethodni = { onGodina(g.godina - 1) },
            onSljedeci = { onGodina(g.godina + 1) },
            onKlik = { onGodina(java.time.LocalDate.now().year) },
            modifier = Modifier.fillMaxWidth(),
        )
        val maks = g.poMjesecima.maxOfOrNull { maxOf(it.prihodi, it.troskovi) }?.coerceAtLeast(1) ?: 1
        val labelPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Prihodi i rashodi po mjesecima ${g.godina}." }) {
            val dolje = size.height - 18.dp.toPx()
            val sirina = size.width / 12
            val stup = (sirina * .3f).coerceAtMost(10.dp.toPx())
            labelPaint.textSize = 10.sp.toPx()
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawLine(Color(0x17FFFFFF), Offset(0f, dolje), Offset(size.width, dolje), 1f)
            g.poMjesecima.forEachIndexed { i, z ->
                val cx = sirina * i + sirina / 2
                fun h(v: Long) = (dolje - 6.dp.toPx()) * v / maks
                drawRoundRect(Neon.Zelena, Offset(cx - stup - 1f, dolje - h(z.prihodi)), Size(stup, h(z.prihodi)), CornerRadius(3.dp.toPx()))
                drawRoundRect(Neon.Crvena, Offset(cx + 1f, dolje - h(z.troskovi)), Size(stup, h(z.troskovi)), CornerRadius(3.dp.toPx()))
                drawIntoCanvas {
                    labelPaint.color = Neon.TekstSiv.toArgb()
                    it.nativeCanvas.drawText(HrDatum.mjeseciKratko[i].take(3), cx, size.height - 4.dp.toPx(), labelPaint)
                }
            }
        }
        val n = g.mjeseciSUnosima.coerceAtLeast(1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stavka("Prihodi", Novac.eur(g.ukupno.prihodi), Neon.Zelena, Modifier.weight(1f))
            Stavka("Rashodi", Novac.eur(g.ukupno.troskovi), Neon.Crvena, Modifier.weight(1f))
            Stavka("Neto", Novac.stanje(g.ukupno.razlika), Neon.LjubicastaTekst, Modifier.weight(1f))
        }
        Text(
            if (g.mjeseciSUnosima == 0) "Nema unosa u ${g.godina}. godini."
            else "Mjesečni prosjek (${g.mjeseciSUnosima} mj. s unosima): prihodi ${Novac.eur(g.ukupno.prihodi / n)}, rashodi ${Novac.eur(g.ukupno.troskovi / n)}",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
        )
        if (g.najveciTroskovi.isNotEmpty()) {
            Text("Najveći troškovi godine", style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Column {
                g.najveciTroskovi.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.transakcija.opis.ifBlank { t.kategorija.naziv }, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1)
                            Text("${t.kategorija.naziv} · ${HrDatum.puni(t.datum())}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                        }
                        Text(Novac.eur(t.transakcija.iznosCenti), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Crvena)
                    }
                }
            }
        }
        if (g.poKategorijama.isNotEmpty()) {
            Text("Rashodi po kategorijama", style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Column {
                g.poKategorijama.forEach { u ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(hexBoja(u.kategorija.bojaHex)))
                        Text(u.kategorija.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                        Text(Novac.postotak(u.udio), style = Tip.oznaka, modifier = Modifier.padding(end = 10.dp))
                        Text(Novac.eur(u.centi), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stavka(naziv: String, vrijednost: String, boja: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(naziv, style = Tip.oznaka)
        Text(vrijednost, color = boja, style = Tip.iznos.copy(fontSize = 14.sp), maxLines = 1)
    }
}
