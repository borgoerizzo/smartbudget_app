package hr.smartbudget.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.TjedniSazetak
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import java.io.File

private val daniKratko = listOf("pon", "uto", "sri", "čet", "pet", "sub", "ned")

private fun raspon(s: TjedniSazetak) = "${HrDatum.kratko(s.od)} – ${HrDatum.kratko(s.doDana)}"

/** Prozor s rekapitulacijom zadnjih 7 dana (iz obavijesti tjednog pregleda). */
@Composable
fun TjedniDijalog(s: TjedniSazetak, test: Boolean, onZatvori: () -> Unit) {
    val ctx = LocalContext.current
    val neto = s.zbroj.razlika
    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B2433), Neon.Pozadina)))
                .border(1.dp, Neon.Zelena.copy(alpha = .25f), RoundedCornerShape(28.dp)),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlocicaIkone(Ikone.ui("calendar"), Neon.Zelena, 44.dp, sjaj = true)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Tjedni pregled", style = Tip.naslov)
                        Text(raspon(s), style = Tip.malo)
                    }
                    KruzniGumb("close", "Zatvori", onZatvori)
                }
                if (test) Text("Testna obavijest: ovo su stvarni podaci zadnjih 7 dana.", color = Neon.Cijan, fontSize = 12.sp)

                // Neto tjedna
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Neto u 7 dana", style = Tip.oznaka)
                    Text(
                        Novac.stanje(neto),
                        color = if (neto >= 0) Neon.Zelena else Neon.Crvena,
                        fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, style = Tip.iznos,
                    )
                    s.promjenaRashoda?.let { p ->
                        Oznaka(
                            (if (p >= 0) "▲ " else "▼ ") + "rashodi ${Novac.postotak(kotlin.math.abs(p))} u odnosu na prethodni tjedan",
                            if (p > 0) Neon.Crvena else Neon.Zelena,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Plocica("Prihodi", Novac.eur(s.zbroj.prihodi), Neon.Zelena, Modifier.weight(1f))
                    Plocica("Rashodi", Novac.eur(s.zbroj.troskovi), Neon.Crvena, Modifier.weight(1f))
                }

                // Rashodi po danima
                Text("Rashodi po danima", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
                val maks = s.poDanima.maxOf { maxOf(it.troskovi, it.prihodi) }.coerceAtLeast(1)
                val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER } }
                Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Rashodi po danima zadnjih 7 dana" }) {
                    val dolje = size.height - 34.dp.toPx()
                    val sirina = size.width / 7
                    val stup = sirina * .46f
                    paint.textSize = 11.sp.toPx()
                    s.poDanima.forEachIndexed { i, d ->
                        val cx = sirina * i + sirina / 2
                        val h = (dolje - 16.dp.toPx()) * d.troskovi / maks
                        drawRoundRect(Color(0x10FFFFFF), Offset(cx - stup / 2, 16.dp.toPx()), Size(stup, dolje - 16.dp.toPx()), CornerRadius(8.dp.toPx()))
                        if (h > 0) {
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Neon.Crvena, Neon.CrvenaJaka), dolje - h, dolje),
                                Offset(cx - stup / 2, dolje - h), Size(stup, h), CornerRadius(8.dp.toPx()),
                            )
                        }
                        if (d.prihodi > 0) drawCircle(Neon.Zelena, 4.dp.toPx(), Offset(cx, 8.dp.toPx()))
                        drawIntoCanvas {
                            paint.color = Neon.TekstSiv.toArgb()
                            it.nativeCanvas.drawText(daniKratko[d.datum.dayOfWeek.value - 1], cx, size.height - 18.dp.toPx(), paint)
                            paint.color = Neon.Tekst.toArgb()
                            it.nativeCanvas.drawText("${d.datum.dayOfMonth}.", cx, size.height - 2.dp.toPx(), paint)
                        }
                    }
                }
                Text(
                    "Prosječno ${Novac.eur(s.prosjekDnevno)} dnevno · ${s.brojUnosa} unosa" + if (s.poDanima.any { it.prihodi > 0 }) " · ● dan s prihodom" else "",
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
                )

                if (s.kategorije.isNotEmpty()) {
                    Text("Najviše potrošeno na", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
                    s.kategorije.take(3).forEach { u ->
                        val b = hexBoja(u.kategorija.bojaHex)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(b))
                                Text(u.kategorija.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                Text("${Novac.eur(u.centi)} · ${Novac.postotak(u.udio, 0)}", style = Tip.iznos.copy(fontSize = 13.sp), color = Neon.TekstSvijetliSiv)
                            }
                            TrakaNapretka((u.udio / 100).toFloat(), Brush.horizontalGradient(listOf(b, b.copy(alpha = .6f))), b, visina = 6.dp)
                        }
                    }
                }
                s.najveciTrosak?.let { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Neon.Ispuna).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Najveći trošak", style = Tip.oznaka)
                            Text(t.transakcija.opis.ifBlank { t.kategorija.naziv }, style = Tip.tijelo, maxLines = 1)
                            Text("${t.kategorija.naziv} · ${HrDatum.sDanom(t.datum())}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                        }
                        Text(Novac.eur(t.transakcija.iznosCenti), color = Neon.Crvena, style = Tip.iznos)
                    }
                }
                if (s.brojUnosa == 0) Text("U zadnjih 7 dana nema unosa.", style = Tip.malo)

                PrimarniGumb("Podijeli kao sliku", onClick = { podijeliSliku(ctx, s) }, ikona = "share")
            }
        }
    }
}

@Composable
private fun Plocica(naziv: String, iznos: String, boja: Color, modifier: Modifier) {
    Column(
        modifier
            .heightIn(min = 64.dp)
            .neonSjaj(boja, 12.dp, 16.dp, .12f)
            .clip(RoundedCornerShape(16.dp))
            .background(boja.copy(alpha = .08f))
            .border(1.dp, boja.copy(alpha = .25f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(naziv, style = Tip.oznaka)
        Text(iznos, color = boja, style = Tip.iznos.copy(fontSize = 17.sp), maxLines = 1)
    }
}

/** Nacrta rekapitulaciju kao sliku 1080×1350 i otvori dijeljenje. */
private fun podijeliSliku(context: Context, s: TjedniSazetak) {
    runCatching {
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Pozadina s blagim neonskim gradijentom
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1B2433.toInt(), 0xFF0B0F14.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        fun tekst(t: String, x: Float, y: Float, vel: Float, boja: Int, debelo: Boolean = false, poravnanje: Paint.Align = Paint.Align.LEFT) {
            p.color = boja; p.textSize = vel; p.textAlign = poravnanje
            p.typeface = Typeface.create(Typeface.DEFAULT, if (debelo) Typeface.BOLD else Typeface.NORMAL)
            c.drawText(t, x, y, p)
        }
        val zelena = Neon.Zelena.toArgb()
        val crvena = Neon.Crvena.toArgb()
        val siva = Neon.TekstSiv.toArgb()
        val bijela = Neon.Tekst.toArgb()
        tekst("SmartBudget · tjedni pregled", 80f, 130f, 40f, siva)
        tekst(raspon(s), 80f, 200f, 56f, bijela, debelo = true)
        val neto = s.zbroj.razlika
        tekst("Neto u 7 dana", 80f, 300f, 36f, siva)
        tekst(Novac.stanje(neto), 80f, 400f, 110f, if (neto >= 0) zelena else crvena, debelo = true)
        tekst("Prihodi", 80f, 490f, 36f, siva)
        tekst(Novac.eur(s.zbroj.prihodi), 80f, 550f, 56f, zelena, debelo = true)
        tekst("Rashodi", 560f, 490f, 36f, siva)
        tekst(Novac.eur(s.zbroj.troskovi), 560f, 550f, 56f, crvena, debelo = true)
        s.promjenaRashoda?.let { pr ->
            tekst((if (pr >= 0) "▲ " else "▼ ") + "rashodi ${Novac.postotak(kotlin.math.abs(pr))} u odnosu na prethodni tjedan", 80f, 620f, 34f, if (pr > 0) crvena else zelena)
        }
        // Stupci po danima
        val vrh = 680f
        val dno = 1060f
        val maks = s.poDanima.maxOf { it.troskovi }.coerceAtLeast(1)
        val sirina = (w - 160f) / 7
        s.poDanima.forEachIndexed { i, d ->
            val cx = 80f + sirina * i + sirina / 2
            val stup = sirina * .5f
            p.color = 0x14FFFFFF
            c.drawRoundRect(RectF(cx - stup / 2, vrh, cx + stup / 2, dno), 22f, 22f, p)
            val hh = (dno - vrh) * d.troskovi / maks
            if (hh > 0) {
                p.color = 0xFFFFFFFF.toInt() // neprozirno: gradijent inače preuzme prozirnost pozadinskog stupca
                p.shader = LinearGradient(0f, dno - hh, 0f, dno, crvena, Neon.CrvenaJaka.toArgb(), Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(cx - stup / 2, dno - hh, cx + stup / 2, dno), 22f, 22f, p)
                p.shader = null
            }
            tekst(daniKratko[d.datum.dayOfWeek.value - 1], cx, dno + 50f, 30f, siva, poravnanje = Paint.Align.CENTER)
            tekst("${d.datum.dayOfMonth}.", cx, dno + 92f, 30f, bijela, poravnanje = Paint.Align.CENTER)
        }
        s.kategorije.firstOrNull()?.let { u ->
            tekst("Najviše: ${u.kategorija.naziv} · ${Novac.eur(u.centi)}", 80f, 1250f, 38f, bijela)
        }
        tekst("Prosječno ${Novac.eur(s.prosjekDnevno)} dnevno · ${s.brojUnosa} unosa", 80f, 1300f, 30f, siva)

        val dir = File(context.cacheDir, "izvoz").apply { mkdirs() }
        val f = File(dir, "tjedni_pregled.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Moj tjedni pregled (${raspon(s)}) · SmartBudget")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Podijeli tjedni pregled").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
