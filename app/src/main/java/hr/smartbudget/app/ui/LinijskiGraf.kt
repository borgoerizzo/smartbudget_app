package hr.smartbudget.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.LocalDate

/**
 * Linijski graf kretanja vrijednosti kroz vrijeme s ispunom ispod linije.
 * Prst na grafu (dodir ili povlačenje) pokazuje datum i vrijednost; bez dodira prikazuje zadnju točku.
 */
@Composable
fun LinijskiGraf(
    tocke: List<Pair<LocalDate, Double>>,
    boja: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    visina: Dp = 170.dp,
) {
    if (tocke.size < 2) return
    var odabrana by remember(tocke) { mutableStateOf<Int?>(null) }
    val i = odabrana ?: tocke.lastIndex
    val min = tocke.minOf { it.second }
    val max = tocke.maxOf { it.second }
    val raspon = (max - min).takeIf { it > 0 } ?: (max.takeIf { it > 0 } ?: 1.0)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(format(tocke[i].second), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, modifier = Modifier.weight(1f))
            Text(HrDatum.puni(tocke[i].first), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        fun indeks(x: Float, sirina: Float) = ((x / sirina) * tocke.lastIndex).toInt().coerceIn(0, tocke.lastIndex)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(visina)
                .semantics { contentDescription = "Graf: od ${format(tocke.first().second)} do ${format(tocke.last().second)}, najviše ${format(max)}" }
                .pointerInput(tocke) { detectTapGestures { odabrana = indeks(it.x, size.width.toFloat()) } }
                .pointerInput(tocke) {
                    detectDragGestures(
                        onDragStart = { odabrana = indeks(it.x, size.width.toFloat()) },
                        onDragEnd = { },
                    ) { promjena, _ -> odabrana = indeks(promjena.position.x, size.width.toFloat()) }
                },
        ) {
            val gore = 8.dp.toPx()
            val dolje = size.height - 4.dp.toPx()
            fun x(k: Int) = size.width * k / tocke.lastIndex
            fun y(v: Double) = (dolje - (dolje - gore) * ((v - min) / raspon)).toFloat()
            // Pomoćne vodoravne linije
            listOf(0.0, .5, 1.0).forEach { f ->
                val yy = gore + (dolje - gore) * f.toFloat()
                drawLine(Color(0x12FFFFFF), Offset(0f, yy), Offset(size.width, yy), 1f)
            }
            val linija = Path().apply {
                moveTo(x(0), y(tocke[0].second))
                for (k in 1..tocke.lastIndex) lineTo(x(k), y(tocke[k].second))
            }
            val ispuna = Path().apply {
                addPath(linija)
                lineTo(size.width, dolje); lineTo(0f, dolje); close()
            }
            drawPath(ispuna, Brush.verticalGradient(listOf(boja.copy(alpha = .32f), boja.copy(alpha = 0f)), startY = gore, endY = dolje))
            drawPath(linija, boja.copy(alpha = .35f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(linija, boja, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Odabrana točka
            val px = x(i); val py = y(tocke[i].second)
            drawLine(Color(0x40FFFFFF), Offset(px, gore), Offset(px, dolje), 1.dp.toPx())
            drawCircle(boja.copy(alpha = .3f), 9.dp.toPx(), Offset(px, py))
            drawCircle(Color.White, 4.dp.toPx(), Offset(px, py))
        }
        Row {
            Text(HrDatum.kratko(tocke.first().first), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp), modifier = Modifier.weight(1f))
            Text("najniže ${format(min)} · najviše ${format(max)}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        }
    }
}
