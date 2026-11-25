package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import hr.smartbudget.app.domain.UstedjenoCilja
import hr.smartbudget.app.domain.ustedjenoCilja
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import hr.smartbudget.app.data.UplataCilja
import hr.smartbudget.app.domain.StatusCilja
import hr.smartbudget.app.domain.analizirajCilj
import hr.smartbudget.app.ui.Oznaka
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private fun kratkiIznos(centi: Long): String =
    if (centi >= 100_00) Novac.cijeli((centi + 50) / 100) + " " + Novac.simbol else Novac.eur(centi)

@Composable
fun CiljeviKartica(
    ciljevi: List<CiljStednje>,
    onNovi: () -> Unit,
    onUredi: (CiljStednje) -> Unit,
    onUplati: (CiljStednje, Boolean) -> Unit,
    uplate: List<UplataCilja> = emptyList(),
    t: hr.smartbudget.app.ui.StanjeTecajeva = hr.smartbudget.app.ui.StanjeTecajeva(),
    onOsvjezi: () -> Unit = {},
    /** Dijeljenje ciljeva kao teksta; null skriva gumb. */
    onPodijeli: (() -> Unit)? = null,
) {
    // Uplate u stranim valutama vrednuju se po današnjem tečaju.
    val imaValuta = uplate.any { u -> u.valuta != null && ciljevi.any { it.id == u.ciljId } }
    LaunchedEffect(imaValuta) { if (imaValuta) onOsvjezi() }
    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ciljevi štednje", style = Tip.naslovKartice)
                Text("Npr. ljetovanje, novi mobitel, rezerva", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            if (onPodijeli != null && ciljevi.isNotEmpty()) {
                hr.smartbudget.app.ui.KruzniGumb("share", "Podijeli ciljeve", onPodijeli, velicina = 40.dp, boja = Neon.Cijan)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            }
            TonalniGumb("Novi", onNovi, ikona = "plus", boja = Neon.Zelena)
        }
        if (ciljevi.isEmpty()) Text("Postavite cilj i pratite koliko ste uštedjeli i koliko treba odvajati.", style = Tip.malo)
        ciljevi.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Neon.RubSlab)
            val ust = ustedjenoCilja(c, uplate) { j, v -> uOsnovnuValutu(j, v, t) }
            StavkaCilja(c.copy(ustedjenoCenti = ust.centi), ust, t, { onUredi(c) }, { onUplati(c, it) })
        }
    }
}

@Composable
private fun StavkaCilja(c: CiljStednje, ust: UstedjenoCilja, t: hr.smartbudget.app.ui.StanjeTecajeva, onUredi: () -> Unit, onUplati: (Boolean) -> Unit) {
    val boja = hexBoja(c.bojaHex)
    val a = analizirajCilj(c)
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi cilj", onClick = onUredi).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrstenCilja(c, a.udio, boja, a.status == StatusCilja.DOSEGNUT, Ikone.ikona(c.ikonaNaziv))
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.naziv, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (ust.valute.isNotEmpty()) "≈ " else ""}${Novac.eur(c.ustedjenoCenti)} od ${Novac.eur(c.ciljCenti)}", style = Tip.malo, maxLines = 1)
                val (tekst, b) = when (a.status) {
                    StatusCilja.DOSEGNUT -> "Dosegnut ✓" to Neon.Zelena
                    StatusCilja.ISPRED_PLANA -> "Ispred plana" to Neon.Zelena
                    StatusCilja.NA_PUTU -> "Na putu" to Neon.Cijan
                    StatusCilja.KASNI -> "Kasni za planom" to Neon.Jantar
                    StatusCilja.ISTEKAO -> "Rok je prošao" to Neon.Crvena
                    StatusCilja.BEZ_ROKA -> "Bez roka" to Neon.TekstSiv
                }
                Oznaka(tekst, b)
            }
        }
        if (ust.valute.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Neon.Ispuna).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("U valutama", style = Tip.oznaka)
                Text(
                    ust.valute.entries.joinToString(" · ") { (k, j) -> hr.smartbudget.app.domain.Valute.iznos(j, hr.smartbudget.app.domain.Valute.po(k)) },
                    style = Tip.tijelo.copy(fontSize = 13.sp), modifier = Modifier.weight(1f), maxLines = 2,
                )
            }
            Text(
                if (t.ucitava) "Preračunavam po današnjem tečaju…" else if (!ust.potpuno) "Za neke valute tečaj nije dostupan, ukupno je nepotpuno." else "Strane valute i kriptovalute uračunate su po današnjem tečaju.",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
            )
        }
        if (a.status != StatusCilja.DOSEGNUT && a.danaDoRoka != null && a.danaDoRoka > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlocicaCilja(animiraniBroj(a.danaDoRoka).toString(), if (a.danaDoRoka == 1L) "dan do roka" else "dana do roka", boja, Modifier.weight(1f))
                PlocicaCilja(kratkiIznos(a.dnevno ?: 0), "dnevno", Neon.Tekst, Modifier.weight(1f))
                PlocicaCilja(kratkiIznos(a.mjesecno ?: 0), "mjesečno", Neon.Tekst, Modifier.weight(1f))
            }
        }
        val ustedjeno = c.ustedjenoCenti
        val poruke = buildList {
            when (a.status) {
                StatusCilja.DOSEGNUT -> add("Bravo! Cilj je dosegnut." to Neon.Zelena)
                StatusCilja.ISPRED_PLANA -> add("Ispred plana si ${Novac.eur(ustedjeno - (a.ocekivanoDoSad ?: 0))}. Tako nastavi!" to Neon.Zelena)
                StatusCilja.NA_PUTU -> add(
                    (if ((a.ocekivanoDoSad ?: 0) == 0L) "Odvajaj ${Novac.eur(a.dnevno ?: 0)} dnevno i cilj stiže na vrijeme."
                    else "Na putu si: po planu je do danas trebalo ${Novac.eur(a.ocekivanoDoSad ?: 0)}.") to Neon.TekstSvijetliSiv,
                )
                StatusCilja.KASNI -> add("Kasniš ${Novac.eur((a.ocekivanoDoSad ?: 0) - ustedjeno)} za planom (do danas je trebalo ${Novac.eur(a.ocekivanoDoSad ?: 0)})." to Neon.Jantar)
                StatusCilja.ISTEKAO -> add("Rok je prošao prije ${-(a.danaDoRoka ?: 0)} dana, nedostaje ${Novac.eur(a.ostalo)}. Produlji rok u uređivanju cilja." to Neon.Crvena)
                StatusCilja.BEZ_ROKA -> add("Još ${Novac.eur(a.ostalo)}. Postavi rok pa ti izračunam koliko odvajati dnevno." to Neon.TekstSvijetliSiv)
            }
            if (a.status != StatusCilja.DOSEGNUT) {
                a.tjedno?.takeIf { it > 0 }?.let { add("Tjedno je to ${Novac.eur(it)}." to Neon.TekstSiv) }
                when {
                    a.procjena != null -> add("Tvoj tempo: ~${Novac.eur(a.tempoMjesecno ?: 0)} mjesečno → cilj ${HrDatum.puni(a.procjena)}." to Neon.TekstSiv)
                    ustedjeno == 0L -> add("Prva uplata pokreće praćenje tempa štednje." to Neon.TekstSiv)
                    else -> add("Tempo štednje računam nakon dva tjedna." to Neon.TekstSiv)
                }
                a.sljedeciPrag?.takeIf { it < 100 }?.let { add("Do $it % još ${Novac.eur(a.doPraga)}." to Neon.TekstSiv) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            poruke.forEach { (t, b) -> Text(t, color = b, fontSize = 13.sp, lineHeight = 18.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalniGumb("Uplati", { onUplati(true) }, Modifier.weight(1f), ikona = "plus", boja = Neon.Zelena)
            if (ustedjeno > 0) TonalniGumb("Povuci", { onUplati(false) }, Modifier.weight(1f), ikona = "minus", boja = Neon.TekstSvijetliSiv)
        }
    }
}

@Composable
private fun PlocicaCilja(vrijednost: String, naziv: String, boja: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna).border(1.dp, Neon.RubSlab, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(vrijednost, color = boja, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        Text(naziv, style = Tip.oznaka.copy(fontSize = 11.sp), maxLines = 1, softWrap = false)
    }
}

/** Broj koji pri prikazu odbroji od nule do vrijednosti. */
@Composable
private fun animiraniBroj(cilj: Long): Long {
    val a = remember { Animatable(0f) }
    LaunchedEffect(cilj) { a.animateTo(cilj.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    return a.value.roundToLong()
}

/**
 * Prsten napretka: pri prikazu se napuni od nule, uplata ga animirano pomakne i iznad iskoči "+iznos".
 * Dosegnut cilj pulsira i oko njega kruže iskrice.
 */
@Composable
private fun PrstenCilja(c: CiljStednje, udio: Float, boja: Color, dosegnut: Boolean, ikona: androidx.compose.ui.graphics.vector.ImageVector) {
    val napredak = remember { Animatable(0f) }
    LaunchedEffect(udio) { napredak.animateTo(udio, tween(1200, easing = FastOutSlowInEasing)) }
    var prethodno by remember(c.id) { mutableLongStateOf(c.ustedjenoCenti) }
    var skok by remember { mutableLongStateOf(0L) }
    val skokAnim = remember { Animatable(1f) }
    LaunchedEffect(c.ustedjenoCenti) {
        val razlika = c.ustedjenoCenti - prethodno
        prethodno = c.ustedjenoCenti
        if (razlika != 0L) {
            skok = razlika
            skokAnim.snapTo(0f)
            skokAnim.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing))
        }
    }
    val beskonacno = rememberInfiniteTransition(label = "cilj")
    val puls by beskonacno.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "puls")
    val kut by beskonacno.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "kut")

    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp).semantics { contentDescription = "Ušteđeno ${(udio * 100).toInt()} posto" }) {
            val deb = 7.dp.toPx()
            val r = size.minDimension / 2 - deb - 4.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            drawCircle(Color(0x14FFFFFF), r, style = Stroke(deb))
            val v = napredak.value
            if (v > 0f) {
                val sjaj = if (dosegnut) .25f + .35f * puls else .28f
                drawArc(boja.copy(alpha = sjaj), -90f, 360f * v, false, tl, sz, style = Stroke(deb + 7.dp.toPx(), cap = StrokeCap.Round))
                drawArc(boja, -90f, 360f * v, false, tl, sz, style = Stroke(deb, cap = StrokeCap.Round))
                // Svijetla točka na vrhu luka
                val k = Math.toRadians(-90.0 + 360.0 * v)
                drawCircle(Color.White.copy(alpha = .9f), deb * .32f, Offset(center.x + (r * cos(k)).toFloat(), center.y + (r * sin(k)).toFloat()))
            }
            if (dosegnut) {
                // Iskrice koje kruže oko prstena
                repeat(6) { i ->
                    val ki = Math.toRadians(kut + i * 60.0)
                    val ri = r + deb + 2.dp.toPx()
                    drawCircle(
                        boja.copy(alpha = .5f + .5f * puls), (1.5f + 1.5f * (i % 2) * puls).dp.toPx(),
                        Offset(center.x + (ri * cos(ki)).toFloat(), center.y + (ri * sin(ki)).toFloat()),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(ikona, null, Modifier.size(16.dp), tint = boja)
            Text("${(napredak.value * 100).roundToInt()} %", color = Neon.Tekst, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
        // "+20 €" koji iskoči iznad prstena nakon uplate
        if (skok != 0L && skokAnim.value < 1f) {
            Text(
                Novac.predznak(skok),
                color = if (skok > 0) Neon.Zelena else Neon.TekstSvijetliSiv, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
                modifier = Modifier.offset(y = (-24 - 26 * skokAnim.value).dp).alpha(1f - skokAnim.value).scale(1f + .25f * (1f - skokAnim.value)),
            )
        }
    }
}

/** Uplata na cilj ili povlačenje s njega; brzi iznosi uključuju višak prošlog ciklusa i ostatak do cilja. */
@Composable
fun UplataCiljaSheet(
    c: CiljStednje,
    uplata: Boolean,
    visakProslog: Long?,
    onPotvrdi: (Long) -> Unit,
    onZatvori: () -> Unit,
    uplate: List<UplataCilja> = emptyList(),
    t: hr.smartbudget.app.ui.StanjeTecajeva = hr.smartbudget.app.ui.StanjeTecajeva(),
    onOsvjezi: () -> Unit = {},
    onUValuti: (Long, hr.smartbudget.app.domain.Valuta) -> Unit = { _, _ -> },
) {
    val osnovna = osnovnaValuta()
    var jeUplata by remember { mutableStateOf(uplata) }
    var valuta by remember { mutableStateOf(osnovna) }
    var iznos by remember { mutableStateOf("") }
    var greska by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onOsvjezi() }
    val ust = ustedjenoCilja(c, uplate) { j, v -> uOsnovnuValutu(j, v, t) }
    val a = analizirajCilj(c.copy(ustedjenoCenti = ust.centi))
    // Za povlačenje: samo valute u kojima na cilju ima novca
    val dostupno: Map<String, Long> = mapOf(osnovna.kod to c.ustedjenoCenti).filterValues { it > 0 } + ust.valute
    val uOsnovnoj = valuta.kod == osnovna.kod
    NeonSheet(onZatvori) {
        ZaglavljeSheeta(c.naziv, onZatvori)
        Segmenti(listOf("Uplati", "Povuci"), if (jeUplata) 0 else 1, {
            jeUplata = it == 0; greska = null
            if (!jeUplata && valuta.kod !in dostupno) valuta = dostupno.keys.firstOrNull()?.let { k -> hr.smartbudget.app.domain.Valute.po(k) } ?: osnovna
        }, boje = listOf(Neon.Zelena, Neon.Jantar))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Valuta")
            GumbValute(
                valuta, Modifier.fillMaxWidth(),
                valute = if (jeUplata) hr.smartbudget.app.domain.Valute.sve.sortedByDescending { it.kod == osnovna.kod }
                else dostupno.keys.map { hr.smartbudget.app.domain.Valute.po(it) },
            ) { valuta = it; iznos = ""; greska = null }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja(if (jeUplata) "Iznos uplate (${valuta.kod})" else "Iznos povlačenja (${valuta.kod})")
            NeonPolje(
                iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(),
                placeholder = if (valuta.kripto) "0,00000000" else "0,00", uCentima = true, decimale = valuta.decimale,
                stil = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = if (jeUplata) Neon.Zelena else Neon.Jantar, fontFeatureSettings = "tnum"),
            )
            if (!uOsnovnoj) {
                val j = hr.smartbudget.app.domain.Valute.parsiraj(iznos, valuta)
                val pre = j?.let { uOsnovnuValutu(it, valuta, t) }
                Text(
                    when {
                        j == null -> "Iznos se preračunava u ${osnovna.kod} po današnjem tečaju."
                        pre != null -> "≈ ${hr.smartbudget.app.domain.Valute.iznos(pre, osnovna)} po današnjem tečaju"
                        t.ucitava -> "Dohvaćam tečaj…"
                        else -> "Tečaj trenutno nije dostupan; iznos će se uračunati kad bude."
                    },
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
                )
                IzvorTecajeva(t, kripto = valuta.kripto, fiat = !valuta.kripto || osnovna.kod != "EUR")
            }
        }
        val brzi = if (!uOsnovnoj) emptyList() else buildList {
            if (jeUplata) {
                a.tjedno?.takeIf { it > 0 }?.let { add("Tjedni iznos" to it) }
                a.mjesecno?.takeIf { it > 0 }?.let { add("Mjesečni iznos" to it) }
                visakProslog?.takeIf { it > 0 }?.let { add("Višak prošlog ciklusa" to it) }
                if (a.ostalo > 0) add("Sve do cilja" to a.ostalo)
            } else if (c.ustedjenoCenti > 0) add("Sve" to c.ustedjenoCenti)
        }
        if (brzi.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                brzi.forEach { (naziv, v) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna)
                            .border(1.dp, Neon.RubSlab, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { iznos = Novac.broj(v); greska = null }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                        Text(Novac.eur(v), style = Tip.iznos.copy(fontSize = 14.sp), color = if (jeUplata) Neon.Zelena else Neon.Jantar)
                    }
                }
            }
        }
        Text(
            "Uplata samo bilježi koliko si odvojio za cilj; stanje računa se ne mijenja. Ako novac prebacuješ na štedni račun, unesi i prijenos.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
        )
        greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
        PrimarniGumb(if (jeUplata) "Uplati" else "Povuci", onClick = {
            if (!uOsnovnoj) {
                val j = hr.smartbudget.app.domain.Valute.parsiraj(iznos, valuta)
                val ima = dostupno[valuta.kod] ?: 0L
                when {
                    j == null || j <= 0 -> greska = if (valuta.kripto) "Unesite iznos (najviše 8 decimala)." else "Unesite iznos."
                    !jeUplata && j > ima -> greska = "Na cilju je samo ${hr.smartbudget.app.domain.Valute.iznos(ima, valuta)}."
                    else -> { onUValuti(if (jeUplata) j else -j, valuta); onZatvori() }
                }
                return@PrimarniGumb
            }
            val v = Novac.parsiraj(iznos)
            when {
                v == null || v <= 0 -> greska = "Unesite iznos."
                !jeUplata && v > c.ustedjenoCenti -> greska = "Na cilju je samo ${Novac.eur(c.ustedjenoCenti)}."
                else -> { onPotvrdi(if (jeUplata) v else -v); onZatvori() }
            }
        }, ikona = "check")
    }
}

private val ikoneCilja = listOf("piggy", "plane", "home", "car", "gift", "phone", "heart", "star")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CiljSheet(
    postojeci: CiljStednje?,
    uplate: List<UplataCilja>,
    onSpremi: (CiljStednje) -> Unit,
    onObrisi: (CiljStednje) -> Unit,
    onZatvori: () -> Unit,
    onObrisiUplatu: (UplataCilja) -> Unit = {},
) {
    var brisemUplatu by remember { mutableStateOf<UplataCilja?>(null) }
    var naziv by remember { mutableStateOf(postojeci?.naziv ?: "") }
    var cilj by remember { mutableStateOf(postojeci?.ciljCenti?.let { Novac.broj(it) } ?: "") }
    var ustedjeno by remember { mutableStateOf(postojeci?.ustedjenoCenti?.let { if (it == 0L) "" else Novac.broj(it) } ?: "") }
    var rok by remember { mutableStateOf(postojeci?.rokEpochDan?.let { LocalDate.ofEpochDay(it) }) }
    var boja by remember { mutableStateOf(postojeci?.bojaHex ?: "#00F5A0") }
    var ikona by remember { mutableStateOf(postojeci?.ikonaNaziv ?: "piggy") }
    var biramRok by remember { mutableStateOf(false) }
    var greska by remember { mutableStateOf<String?>(null) }
    var potvrda by remember { mutableStateOf(false) }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(if (postojeci == null) "Novi cilj štednje" else "Cilj štednje", onZatvori)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Naziv cilja")
            NeonPolje(naziv, { naziv = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "npr. Ljetovanje")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Ciljni iznos (${Novac.simbol})")
            NeonPolje(
                cilj, { cilj = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true,
                stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena, fontFeatureSettings = "tnum"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Već ušteđeno, bez uplata u drugim valutama (${Novac.simbol})")
            NeonPolje(ustedjeno, { ustedjeno = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
            if (postojeci != null) Text("Za redovite uplate koristi gumb Uplati na kartici cilja.", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GumbPolja("calendar", rok?.let { "Rok ${HrDatum.puni(it)}" } ?: "Rok (opcionalno)", Modifier.weight(1f)) { biramRok = true }
            if (rok != null) KruzniGumb("close", "Ukloni rok", { rok = null })
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Ikona i boja")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ikoneCilja.forEach { n ->
                    val on = n == ikona
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) hexBoja(boja).copy(alpha = .22f) else Neon.Ispuna)
                            .border(1.dp, if (on) hexBoja(boja) else Neon.RubSlab, RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { ikona = n },
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Icon(Ikone.ikona(n), null, Modifier.size(22.dp), tint = if (on) hexBoja(boja) else Neon.TekstSrednji) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Neon.paleta.forEach { hex ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(hexBoja(hex))
                            .border(2.dp, if (hex == boja) Neon.Tekst else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(15.dp))
                            .clickable(role = Role.RadioButton) { boja = hex },
                    )
                }
            }
        }
        greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
        PrimarniGumb("Spremi cilj", onClick = {
            val c = Novac.parsiraj(cilj)
            when {
                naziv.isBlank() -> greska = "Unesite naziv cilja."
                c == null || c <= 0 -> greska = "Unesite ciljni iznos."
                else -> {
                    onSpremi(
                        CiljStednje(
                            id = postojeci?.id ?: 0, naziv = naziv.trim(), ciljCenti = c,
                            ustedjenoCenti = Novac.parsiraj(ustedjeno) ?: 0L, rokEpochDan = rok?.toEpochDay(),
                            bojaHex = boja, ikonaNaziv = ikona, kreiranEpochDan = postojeci?.kreiranEpochDan,
                        ),
                    )
                    onZatvori()
                }
            }
        }, ikona = "check")
        val povijest = uplate.filter { it.ciljId == postojeci?.id }
        if (povijest.isNotEmpty()) {
            Column {
                OznakaPolja("Povijest uplata · pogrešan unos obrišite ikonom")
                povijest.forEachIndexed { i, u ->
                    if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(HrDatum.puni(LocalDate.ofEpochDay(u.epochDan)), style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                        Text(
                            if (u.valuta == null) Novac.predznak(u.iznosCenti)
                            else (if (u.iznosCenti >= 0) "+" else "") + hr.smartbudget.app.domain.Valute.iznos(u.iznosCenti, hr.smartbudget.app.domain.Valute.po(u.valuta)),
                            style = Tip.iznos.copy(fontSize = 14.sp), color = if (u.iznosCenti >= 0) Neon.Zelena else Neon.Jantar,
                        )
                        Spacer(Modifier.width(10.dp))
                        KruzniGumb("trash", "Obriši unos", { brisemUplatu = u }, velicina = 34.dp, boja = Neon.Crvena)
                    }
                }
            }
        }
        if (postojeci != null) GumbBrisanja("Obriši cilj") { potvrda = true }
    }
    brisemUplatu?.let { u ->
        val iznosTekst = if (u.valuta == null) Novac.predznak(u.iznosCenti)
        else (if (u.iznosCenti >= 0) "+" else "") + hr.smartbudget.app.domain.Valute.iznos(u.iznosCenti, hr.smartbudget.app.domain.Valute.po(u.valuta))
        PotvrdaBrisanja(
            "Obrisati unos?",
            "${HrDatum.puni(LocalDate.ofEpochDay(u.epochDan))} · $iznosTekst\nUnos se uklanja iz cilja kao da nikad nije upisan.",
            { brisemUplatu = null },
        ) {
            onObrisiUplatu(u)
            // Polje "Već ušteđeno" prati brisanje, da ga spremanje cilja ne vrati
            if (u.valuta == null) ustedjeno = ((Novac.parsiraj(ustedjeno) ?: 0L) - u.iznosCenti).coerceAtLeast(0).let { if (it == 0L) "" else Novac.broj(it) }
            brisemUplatu = null
        }
    }
    if (biramRok) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (rok ?: LocalDate.now().plusMonths(6)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { biramRok = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { rok = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    biramRok = false
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramRok = false }) { Text("Odustani", color = Neon.TekstSiv) } },
            colors = DatePickerDefaults.colors(containerColor = Neon.Sheet),
        ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = Neon.Sheet, selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu)) }
    }
    if (potvrda && postojeci != null) {
        PotvrdaBrisanja("Obrisati cilj?", "Cilj \"${postojeci.naziv}\" bit će obrisan.", { potvrda = false }) {
            onObrisi(postojeci); potvrda = false; onZatvori()
        }
    }
}

/** Iznos u najmanjim jedinicama valute [v] preračunat u osnovnu valutu (decimalno); null bez tečaja. */
fun uOsnovnuValutu(jedinice: Long, v: hr.smartbudget.app.domain.Valuta, t: hr.smartbudget.app.ui.StanjeTecajeva): Double? {
    val o = osnovnaValuta()
    return if (v.kod == o.kod) hr.smartbudget.app.domain.Valute.uBroj(jedinice, v) else t.pretvori(jedinice, v, o)
}
