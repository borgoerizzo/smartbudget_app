package hr.smartbudget.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.domain.StatusLimita
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import hr.smartbudget.app.ui.theme.svjetlija

/** Neonski sjaj iza elementa (zamućena sjena u boji). */
fun Modifier.neonSjaj(boja: Color, radijus: Dp = 18.dp, zaobljenje: Dp = 20.dp, alfa: Float = 0.35f): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(radijus.toPx(), 0f, 0f, boja.copy(alpha = alfa).toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, zaobljenje.toPx(), zaobljenje.toPx(), paint)
    }
}

/** Tamna pozadina s blagim neonskim mrljama u kutovima, kao u dizajnu. */
@Composable
fun PozadinaEkrana(
    gore: Color = Neon.Zelena.copy(alpha = .10f),
    desno: Color = Color(0x337B2CBF),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Neon.Pozadina)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(gore, Color.Transparent), Offset(0f, 0f), size.width * 1.0f), size.width * 1.0f, Offset(0f, 0f))
                drawCircle(
                    Brush.radialGradient(listOf(desno, Color.Transparent), Offset(size.width, size.height * .08f), size.width * .95f),
                    size.width * .95f, Offset(size.width, size.height * .08f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x14FF2A6D), Color.Transparent), Offset(size.width / 2, size.height), size.width * 1.1f),
                    size.width * 1.1f, Offset(size.width / 2, size.height),
                )
            },
        content = content,
    )
}

@Composable
fun Kartica(
    modifier: Modifier = Modifier,
    zaobljenje: Dp = 24.dp,
    pozadina: Color = Neon.Kartica,
    rub: Color = Color(0x14FFFFFF),
    sjaj: Color? = null,
    padding: Dp = 16.dp,
    razmak: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val oblik = RoundedCornerShape(zaobljenje)
    Column(
        modifier
            .then(if (sjaj != null) Modifier.neonSjaj(sjaj, 24.dp, zaobljenje, .28f) else Modifier)
            .clip(oblik)
            .background(pozadina)
            .border(1.dp, rub, oblik)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(razmak),
        content = content,
    )
}

@Composable
fun NeonChip(tekst: String, odabran: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, boja: Color = Neon.Zelena) {
    val oblik = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(44.dp)
            .then(if (odabran) Modifier.neonSjaj(boja, 14.dp, 22.dp, .25f) else Modifier)
            .clip(oblik)
            .background(if (odabran) boja.copy(alpha = .14f) else Color(0xB31A222D))
            .border(1.dp, if (odabran) boja.copy(alpha = .5f) else Neon.Rub, oblik)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tekst, color = if (odabran) boja else Neon.TekstSvijetliSiv, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Pill kategorije: ikona + naziv u boji kategorije. */
@Composable
fun PillKategorije(k: Kategorija, modifier: Modifier = Modifier) {
    val boja = hexBoja(k.bojaHex)
    Row(
        modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(boja.copy(alpha = .16f))
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Ikone.ikona(k.ikonaNaziv), null, Modifier.size(14.dp), tint = boja.svjetlija())
        Text(k.naziv, color = boja.svjetlija(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Kvadratić 44 dp s ikonom kategorije. */
@Composable
fun PlocicaKategorije(k: Kategorija, velicina: Dp = 44.dp, sjaj: Boolean = false) {
    PlocicaIkone(Ikone.ikona(k.ikonaNaziv), hexBoja(k.bojaHex), velicina, sjaj)
}

@Composable
fun PlocicaIkone(ikona: androidx.compose.ui.graphics.vector.ImageVector, boja: Color, velicina: Dp = 44.dp, sjaj: Boolean = false) {
    Box(
        Modifier
            .size(velicina)
            .then(if (sjaj) Modifier.neonSjaj(boja, 16.dp, 14.dp, .5f) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(boja.copy(alpha = .18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ikona, null, Modifier.size(velicina / 2), tint = boja.svjetlija())
    }
}

/** Traka napretka s neonskom ispunom i opcionalnim markerom praga (0–1). */
@Composable
fun TrakaNapretka(
    udio: Float,
    ispuna: Brush,
    sjaj: Color,
    modifier: Modifier = Modifier,
    visina: Dp = 10.dp,
    marker: Float? = null,
) {
    Box(modifier.fillMaxWidth().height(visina + 8.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(visina).clip(RoundedCornerShape(visina / 2)).background(Neon.Staza))
        if (udio > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(udio.coerceIn(0.02f, 1f))
                    .height(visina)
                    .neonSjaj(sjaj, 10.dp, visina / 2, .45f)
                    .clip(RoundedCornerShape(visina / 2))
                    .background(ispuna),
            )
        }
        if (marker != null) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .offset(x = maxWidth * marker.coerceIn(0f, 1f) - 1.dp)
                        .width(2.dp)
                        .height(visina + 6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Neon.Tekst.copy(alpha = .6f)),
                )
            }
        }
    }
}

data class StilStatusa(val boja: Color, val ispuna: Brush, val naziv: String, val rub: Color, val ikona: String)

fun stilStatusa(s: StatusLimita): StilStatusa = when (s) {
    StatusLimita.PREKORACENO -> StilStatusa(Neon.Crvena, Brush.horizontalGradient(listOf(Neon.CrvenaJaka, Neon.Crvena)), "Prekoračeno", Neon.Crvena.copy(alpha = .55f), "x")
    StatusLimita.BLIZU -> StilStatusa(Neon.Jantar, Brush.horizontalGradient(listOf(Neon.Jantar, Neon.Narancasta)), "Blizu limita", Neon.Jantar.copy(alpha = .22f), "warn")
    StatusLimita.SIGURNO -> StilStatusa(Neon.Zelena, Brush.horizontalGradient(listOf(Neon.Zelena, Neon.Cijan)), "Sigurno", Color(0x14FFFFFF), "check")
    StatusLimita.BEZ_LIMITA -> StilStatusa(Neon.TekstSiv, Brush.horizontalGradient(listOf(Neon.Neaktivno, Neon.Neaktivno)), "Bez limita", Color(0x14FFFFFF), "x")
}

/** Primarni gumb: gradijent smaragd → cijan, 56 dp. */
@Composable
fun PrimarniGumb(tekst: String, onClick: () -> Unit, modifier: Modifier = Modifier, ikona: String? = null, omogucen: Boolean = true) {
    val oblik = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (omogucen) Modifier.neonSjaj(Neon.Zelena, 20.dp, 28.dp, .35f) else Modifier)
            .clip(oblik)
            .background(
                if (omogucen) Brush.linearGradient(listOf(Neon.Zelena, Neon.Cijan))
                else Brush.linearGradient(listOf(Neon.Neaktivno, Neon.Neaktivno)),
            )
            .border(1.dp, Color(0x40FFFFFF), oblik)
            .clickable(enabled = omogucen, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ikona != null) {
            Icon(Ikone.ui(ikona), null, Modifier.size(20.dp), tint = Neon.NaGumbu)
            Spacer(Modifier.width(8.dp))
        }
        Text(tekst, color = Neon.NaGumbu, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Sekundarni (tonalni) gumb, 44–48 dp. */
@Composable
fun TonalniGumb(
    tekst: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikona: String? = null,
    boja: Color = Neon.Zelena,
    visina: Dp = 44.dp,
) {
    val oblik = RoundedCornerShape(visina / 2)
    Row(
        modifier
            .height(visina)
            .clip(oblik)
            .background(boja.copy(alpha = .14f))
            .border(1.dp, boja.copy(alpha = .5f), oblik)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (ikona != null) 12.dp else 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ikona != null) {
            Icon(Ikone.ui(ikona), null, Modifier.size(18.dp), tint = boja)
            Spacer(Modifier.width(6.dp))
        }
        Text(tekst, color = boja, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KruzniGumb(ikona: String, opis: String, onClick: () -> Unit, modifier: Modifier = Modifier, velicina: Dp = 44.dp, boja: Color = Neon.Tekst) {
    Box(
        modifier
            .size(velicina)
            .clip(CircleShape)
            .background(Color(0xB31A222D))
            .border(1.dp, Neon.Rub, CircleShape)
            .clickable(role = Role.Button, onClickLabel = opis, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ikone.ui(ikona), opis, Modifier.size(20.dp), tint = boja)
    }
}

@Composable
fun Oznaka(tekst: String, boja: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 24.dp).clip(RoundedCornerShape(12.dp)).background(boja.copy(alpha = .13f)).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tekst, color = boja, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = Tip.oznaka)
    }
}

/** Gumb za navigaciju kroz cikluse: ‹ Ciklus: 15. lis – 14. stu › */
@Composable
fun BiracCiklusa(tekst: String, onPrethodni: () -> Unit, onSljedeci: () -> Unit, onKlik: () -> Unit, modifier: Modifier = Modifier) {
    val oblik = RoundedCornerShape(24.dp)
    Row(
        modifier
            .height(48.dp)
            .clip(oblik)
            .background(Color(0xB31A222D))
            .border(1.dp, Neon.Rub, oblik),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = "Prethodni ciklus", onClick = onPrethodni),
            contentAlignment = Alignment.Center,
        ) { Icon(Ikone.ui("chevLeft"), "Prethodni ciklus", Modifier.size(18.dp), tint = Neon.TekstSiv) }
        Row(
            Modifier.weight(1f).height(48.dp).clickable(role = Role.Button, onClickLabel = "Trenutni ciklus", onClick = onKlik),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Ikone.ui("calendar"), null, Modifier.size(18.dp), tint = Neon.Cijan)
            Spacer(Modifier.width(8.dp))
            Text(tekst, style = Tip.tijelo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = "Sljedeći ciklus", onClick = onSljedeci),
            contentAlignment = Alignment.Center,
        ) { Icon(Ikone.ui("chevRight"), "Sljedeći ciklus", Modifier.size(18.dp), tint = Neon.TekstSiv) }
    }
}

enum class Ekran(val naziv: String, val ikona: String) {
    POCETNA("Početna", "navHome"),
    ANALITIKA("Analitika", "navChart"),
    BUDZETI("Budžeti", "navGrid"),
    POSTAVKE("Postavke", "navSliders"),
}

@Composable
fun DonjaNavigacija(trenutni: Ekran, onOdabir: (Ekran) -> Unit, modifier: Modifier = Modifier) {
    val oblik = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .neonSjaj(Color.Black, 30.dp, 28.dp, .55f)
            .clip(oblik)
            .background(Neon.Nav)
            .border(1.dp, Neon.Rub, oblik)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ekran.entries.forEach { e ->
            val aktivan = e == trenutni
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Tab, onClick = { onOdabir(e) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(30.dp)
                        .then(if (aktivan) Modifier.neonSjaj(Neon.Zelena, 14.dp, 15.dp, .35f) else Modifier)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (aktivan) Neon.Zelena.copy(alpha = .16f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ikone.ui(e.ikona), null, Modifier.size(22.dp), tint = if (aktivan) Neon.Zelena else Neon.TekstSiv)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    e.naziv,
                    color = if (aktivan) Neon.Zelena else Neon.TekstSiv,
                    fontSize = 12.sp,
                    fontWeight = if (aktivan) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun NaslovSekcije(tekst: String, modifier: Modifier = Modifier) {
    Text(tekst, style = Tip.naslovKartice, modifier = modifier.padding(horizontal = 4.dp))
}

@Composable
fun PraznoStanje(naslov: String, tekst: String, modifier: Modifier = Modifier, akcija: String? = null, onAkcija: () -> Unit = {}) {
    Kartica(modifier.fillMaxWidth(), padding = 20.dp, razmak = 10.dp) {
        Text(naslov, style = Tip.tijelo)
        Text(tekst, style = Tip.malo)
        if (akcija != null) TonalniGumb(akcija, onAkcija, ikona = "plus")
    }
}
