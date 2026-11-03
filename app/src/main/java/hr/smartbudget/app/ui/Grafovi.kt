package hr.smartbudget.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.UdioKategorije
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja

/** Kružni (donut) graf udjela kategorija s ukupnim iznosom u sredini. */
@Composable
fun KruzniGraf(
    udjeli: List<UdioKategorije>,
    naslovSredine: String,
    ukupnoCenti: Long,
    modifier: Modifier = Modifier,
    velicina: Dp = 196.dp,
) {
    Box(modifier.size(velicina), contentAlignment = Alignment.Center) {
        val opis = "Kružni graf, $naslovSredine: " + udjeli.joinToString { "${it.kategorija.naziv} ${Novac.postotak(it.udio)}" }
        Canvas(Modifier.size(velicina).semantics { contentDescription = opis }) {
            val debljina = 20.dp.toPx()
            val rad = size.minDimension / 2 - debljina / 2 - 6.dp.toPx()
            val tl = Offset(center.x - rad, center.y - rad)
            val sz = Size(rad * 2, rad * 2)
            drawCircle(Color(0x0DFFFFFF), rad, style = Stroke(debljina))
            var kut = -90f
            udjeli.forEach { u ->
                val sweep = (u.udio * 3.6).toFloat()
                val boja = hexBoja(u.kategorija.bojaHex)
                // Mali razmak između isječaka; sjaj je širi, prozirniji potez ispod.
                val luk = (sweep - 1.2f).coerceAtLeast(.5f)
                drawArc(boja.copy(alpha = .25f), kut, luk, false, tl, sz, style = Stroke(debljina + 8.dp.toPx()))
                drawArc(boja, kut, luk, false, tl, sz, style = Stroke(debljina))
                kut += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(naslovSredine, style = Tip.oznaka)
            Text(Novac.eur(ukupnoCenti), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, style = Tip.iznos, color = Neon.Tekst)
        }
    }
}
