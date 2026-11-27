package hr.smartbudget.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.FormatTrzista
import hr.smartbudget.app.data.GrafCijene
import hr.smartbudget.app.data.Kotacija
import hr.smartbudget.app.data.ListaUlaganja
import hr.smartbudget.app.data.RazdobljeGrafa
import hr.smartbudget.app.data.TockaGrafa
import hr.smartbudget.app.data.Trziste
import hr.smartbudget.app.data.VrstaUlaganja
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/*
 * Sekcija "Ulaganja": pregled tržišta po vrstama ulaganja (dionice, ETF-ovi, trezorski zapisi, roba, kripto),
 * liste kao "Najtrgovanije" i "Preporuka analitičara", te detalj instrumenta s grafom po kojem se klizi prstom.
 * Samo prikaz podataka; aplikacija ne omogućuje trgovanje.
 */

private val VrstaUlaganja.boja: Color
    get() = when (this) {
        VrstaUlaganja.DIONICE -> Neon.Zelena
        VrstaUlaganja.ETF -> Neon.Cijan
        VrstaUlaganja.OBVEZNICE -> Neon.LjubicastaSvijetla
        VrstaUlaganja.ROBA -> Neon.Jantar
        VrstaUlaganja.KRIPTO -> Neon.Ljubicasta
    }

private sealed interface StanjeListe {
    data object Ucitava : StanjeListe
    data class Gotovo(val liste: List<ListaUlaganja>, val mini: Map<String, List<Double>>) : StanjeListe
    data class Greska(val poruka: String) : StanjeListe
}

private fun bojaPromjene(p: Double) = when {
    p > 0 -> Neon.Zelena
    p < 0 -> Neon.Crvena
    else -> Neon.TekstSiv
}

@Composable
fun UlaganjaEkran() {
    var vrsta by rememberSaveable { mutableStateOf(VrstaUlaganja.DIONICE) }
    var odabrana by remember { mutableStateOf<Kotacija?>(null) }
    var osvjezi by remember { mutableIntStateOf(0) }
    val stanjeListe = rememberLazyListState()
    val stanje by produceState<StanjeListe>(StanjeListe.Ucitava, vrsta, osvjezi) {
        value = StanjeListe.Ucitava
        value = runCatching { Trziste.liste(vrsta) }.fold(
            { (l, m) -> StanjeListe.Gotovo(l, m) },
            { StanjeListe.Greska(it.message ?: it.javaClass.simpleName) },
        )
    }

    odabrana?.let { k ->
        InstrumentEkran(k) { odabrana = null }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        state = stanjeListe,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ulaganja", style = Tip.naslovVeliki)
                    Text("Tržišni podaci s odgodom · bez trgovanja", style = Tip.malo)
                }
                KruzniGumb("osvjezi", "Osvježi podatke", { osvjezi++ }, velicina = 44.dp)
            }
        }
        item { BiracVrste(vrsta) { vrsta = it } }
        when (val s = stanje) {
            StanjeListe.Ucitava -> item {
                Kartica(Modifier.fillMaxWidth(), padding = 24.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = vrsta.boja, strokeWidth = 2.5.dp)
                        Text("Učitavam tržišne podatke…", style = Tip.tijelo)
                    }
                }
            }
            is StanjeListe.Greska -> item {
                Kartica(Modifier.fillMaxWidth(), padding = 20.dp, razmak = 10.dp) {
                    Text("Podaci trenutno nisu dostupni", style = Tip.tijelo)
                    Text("Provjerite vezu s internetom. (${s.poruka})", style = Tip.malo)
                    TonalniGumb("Pokušaj ponovno", { osvjezi++ }, ikona = "osvjezi", boja = vrsta.boja)
                }
            }
            is StanjeListe.Gotovo -> {
                s.liste.filter { it.stavke.isNotEmpty() }.forEach { l ->
                    item(key = "${vrsta.name}_${l.naslov}") {
                        if (l.naslov == "Indeksi") RedIndeksa(l.stavke, s.mini) { odabrana = it }
                        else KarticaListe(l, vrsta.boja, s.mini) { odabrana = it }
                    }
                }
            }
        }
        item { Napomena() }
    }
}

@Composable
private fun BiracVrste(odabrana: VrstaUlaganja, onOdabir: (VrstaUlaganja) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VrstaUlaganja.entries.forEach { v ->
            val aktivna = v == odabrana
            val oblik = RoundedCornerShape(20.dp)
            // Ikona u sredini pločice, naziv centriran pri dnu.
            Box(
                Modifier
                    .width(96.dp)
                    .height(112.dp)
                    .then(if (aktivna) Modifier.neonSjaj(v.boja, 14.dp, 20.dp, .3f) else Modifier)
                    .clip(oblik)
                    .background(if (aktivna) v.boja.copy(alpha = .14f) else Neon.Kartica)
                    .border(1.dp, if (aktivna) v.boja.copy(alpha = .55f) else Neon.Rub, oblik)
                    .clickable(onClickLabel = v.naziv) { onOdabir(v) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier.align(Alignment.Center).padding(bottom = 18.dp).size(44.dp).clip(CircleShape).background(v.boja.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ikone.ui(v.ikona), null, Modifier.size(24.dp), tint = v.boja)
                }
                Text(
                    v.naslovPlocice, color = if (aktivna) Neon.Tekst else Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RedIndeksa(stavke: List<Kotacija>, mini: Map<String, List<Double>>, onOdabir: (Kotacija) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        stavke.forEach { k ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Neon.Kartica)
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(18.dp))
                    .clickable(onClickLabel = "Otvori ${k.naziv}") { onOdabir(k) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(k.naziv, style = Tip.oznaka, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MiniGraf(mini[k.simbol].orEmpty(), bojaPromjene(k.promjenaPosto), Modifier.fillMaxWidth().height(26.dp))
                Text(FormatTrzista.broj(k.cijena, 2), color = Neon.Tekst, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(FormatTrzista.promjenaPosto(k.promjenaPosto), color = bojaPromjene(k.promjenaPosto), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun KarticaListe(l: ListaUlaganja, boja: Color, mini: Map<String, List<Double>>, onOdabir: (Kotacija) -> Unit) {
    var sve by rememberSaveable(l.naslov) { mutableStateOf(false) }
    // Najtrgovanije: promet danas, prosjek zadnjih 10 dana ili 3 mjeseca.
    var dana by rememberSaveable(l.naslov) { mutableIntStateOf(1) }
    val stavke = if (l.mjeriVolumen) Trziste.poPrometu(l.stavke, dana) else l.stavke
    val prikazane = stavke.take(if (sve) 15 else 5)
    Kartica(Modifier.fillMaxWidth(), padding = 0.dp, razmak = 0.dp) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(l.naslov, style = Tip.naslovKartice)
            Text(l.opis, style = Tip.malo)
            if (l.mjeriVolumen && l.stavke.any { it.prosjVolumen3m != null }) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Danas", 10 to "10 dana", 90 to "3 mjeseca").forEach { (d, t) ->
                        val ok = d == dana
                        Text(
                            t, color = if (ok) boja else Neon.TekstSiv, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (ok) boja.copy(alpha = .14f) else Color(0x0FFFFFFF))
                                .clickable(onClickLabel = "Promet: $t") { dana = d }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        prikazane.forEach { k ->
            val podnaslov = when {
                l.mjeriVolumen -> {
                    val v = when (dana) { 1 -> k.volumen; 10 -> k.prosjVolumen10 ?: k.volumen; else -> k.prosjVolumen3m ?: k.volumen }
                    v?.let { kratkiSimbol(k.simbol) + " · " + FormatTrzista.veliki(it.toDouble()) } ?: k.simbol
                }
                l.naslov.startsWith("Preporuka") && k.ocjena != null ->
                    "${kratkiSimbol(k.simbol)} · ${k.ocjena.oznaka} ${FormatTrzista.broj(k.ocjena.vrijednost, 1)}"
                else -> kratkiSimbol(k.simbol)
            }
            RedInstrumenta(k, podnaslov, mini[k.simbol].orEmpty()) { onOdabir(k) }
        }
        if (stavke.size > 5) {
            Text(
                if (sve) "Prikaži manje" else "Prikaži više",
                color = boja, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { sve = !sve }.padding(vertical = 14.dp),
            )
        } else Spacer(Modifier.height(8.dp))
    }
}

/** Kratka oznaka bez tehničkih nastavaka (^, =F, -USD). */
internal fun kratkiSimbol(s: String) = s.removePrefix("^").substringBefore("=").substringBefore("-").substringBefore(".")

private fun oznakaSimbola(k: Kotacija) = kratkiSimbol(k.simbol) + (k.burza?.let { " · $it" } ?: "")

@Composable
internal fun Avatar(k: Kotacija, velicina: Dp = 40.dp) = AvatarSimbola(k.simbol, velicina)

@Composable
internal fun AvatarSimbola(simbol: String, velicina: Dp = 40.dp) {
    val paleta = listOf(Neon.Zelena, Neon.Cijan, Neon.Ljubicasta, Neon.Jantar, Neon.Crvena, Neon.LjubicastaSvijetla)
    val boja = paleta[abs(simbol.hashCode()) % paleta.size]
    val tekst = kratkiSimbol(simbol).take(4)
    Box(Modifier.size(velicina).clip(CircleShape).background(boja.copy(alpha = .16f)).border(1.dp, boja.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
        Text(tekst, color = boja, fontSize = if (tekst.length > 3) 10.sp else 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun RedInstrumenta(k: Kotacija, podnaslov: String, mini: List<Double>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Otvori ${k.naziv}", onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(k, 38.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(k.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(podnaslov, style = Tip.malo.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mini.size >= 2) {
            MiniGraf(mini, bojaPromjene(k.promjenaPosto), Modifier.width(40.dp).height(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(FormatTrzista.cijena(k.cijena, k.valuta, k.jePrinos), color = Neon.Tekst, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(FormatTrzista.promjenaPosto(k.promjenaPosto), color = bojaPromjene(k.promjenaPosto), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiniGraf(tocke: List<Double>, boja: Color, modifier: Modifier) {
    if (tocke.size < 2) {
        Box(modifier); return
    }
    Canvas(modifier) {
        val min = tocke.min()
        val raspon = (tocke.max() - min).takeIf { it > 0 } ?: 1.0
        val put = Path()
        tocke.forEachIndexed { i, v ->
            val x = size.width * i / tocke.lastIndex
            val y = (size.height - size.height * ((v - min) / raspon)).toFloat()
            if (i == 0) put.moveTo(x, y) else put.lineTo(x, y)
        }
        drawPath(put, boja, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Napomena() {
    Text(
        "Podaci: Yahoo Finance (javni podaci, s odgodom). Ocjene su konsenzus vanjskih analitičara i nisu savjet za ulaganje. " +
            "SmartBudget ne omogućuje kupnju ni prodaju; odluke o ulaganju donosite sami, na platformi po izboru.",
        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

// --- Detalj instrumenta ---

private fun opisRazdoblja(r: RazdobljeGrafa) = when (r) {
    RazdobljeGrafa.DAN -> "danas"
    RazdobljeGrafa.TJEDAN -> "zadnji tjedan"
    RazdobljeGrafa.MJESEC -> "zadnji mjesec"
    RazdobljeGrafa.SEST_MJESECI -> "zadnjih 6 mjeseci"
    RazdobljeGrafa.GODINA -> "zadnju godinu"
    RazdobljeGrafa.PET_GODINA -> "zadnjih 5 godina"
    RazdobljeGrafa.SVE -> "od početka"
}

private fun vrijemeTocke(ms: Long, sVremenom: Boolean): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (sVremenom) HrDatum.kratko(t.toLocalDate()) + " " + HrDatum.vrijeme(t.toLocalDateTime()) else HrDatum.puni(t.toLocalDate())
}

@Composable
private fun InstrumentEkran(osnova: Kotacija, onNatrag: () -> Unit) {
    BackHandler(onBack = onNatrag)
    val ctx = LocalContext.current
    var razdoblje by rememberSaveable(osnova.simbol) { mutableStateOf(RazdobljeGrafa.DAN) }
    // Povijest po razdoblju ostaje dok je ekran otvoren (prebacivanje razdoblja bez ponovnog čekanja).
    val ucitano = remember(osnova.simbol) { mutableStateMapOf<RazdobljeGrafa, GrafCijene>() }
    var greska by remember { mutableStateOf<String?>(null) }
    var pokusaj by remember { mutableIntStateOf(0) }
    val graf = ucitano[razdoblje]
    androidx.compose.runtime.LaunchedEffect(osnova.simbol, razdoblje, pokusaj) {
        if (ucitano[razdoblje] == null) {
            greska = null
            runCatching { Trziste.graf(osnova, razdoblje) }
                .onSuccess { ucitano[razdoblje] = it }
                .onFailure { greska = it.message ?: "nema veze" }
        }
    }
    var odabrani by remember(graf) { mutableStateOf<Int?>(null) }

    val k = graf?.kotacija ?: ucitano.values.firstOrNull()?.kotacija ?: osnova
    val tocke = graf?.tocke.orEmpty()
    val pocetna = if (razdoblje == RazdobljeGrafa.DAN) graf?.pocetak ?: k.prethodnoZatvaranje else tocke.firstOrNull()?.vrijednost
    val vrijednost = odabrani?.let { tocke.getOrNull(it)?.vrijednost } ?: if (razdoblje == RazdobljeGrafa.DAN || tocke.isEmpty()) k.cijena else tocke.last().vrijednost
    val promjena = pocetna?.let { vrijednost - it }
    val posto = if (pocetna != null && pocetna != 0.0) promjena!! / pocetna * 100 else null
    val bojaGrafa = bojaPromjene(((tocke.lastOrNull()?.vrijednost ?: k.cijena) - (pocetna ?: k.cijena)))

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KruzniGumb("chevLeft", "Natrag", onNatrag, velicina = 44.dp)
            Spacer(Modifier.weight(1f))
            Avatar(k, 44.dp)
        }
        // Zaglavlje: vrijednost odabrane točke dok prst klizi po grafu, inače trenutna cijena.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(k.naziv, style = Tip.naslovKartice, maxLines = 2)
            Text(oznakaSimbola(k), style = Tip.malo)
            Spacer(Modifier.height(6.dp))
            Text(
                FormatTrzista.cijena(vrijednost, k.valuta, k.jePrinos),
                color = Neon.Tekst, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (promjena != null && posto != null) {
                    Text(
                        FormatTrzista.promjena(promjena, k.valuta, k.jePrinos) + "  (" + FormatTrzista.promjenaPosto(posto) + ")",
                        color = bojaPromjene(promjena), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    odabrani?.let { tocke.getOrNull(it) }?.let { vrijemeTocke(it.vrijeme, razdoblje.sVremenom) } ?: opisRazdoblja(razdoblje),
                    style = Tip.malo,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            when {
                tocke.size >= 2 -> GrafUlaganja(tocke, pocetna, bojaGrafa, odabrani) { odabrani = it }
                greska != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Graf nije dostupan", style = Tip.tijelo)
                    TonalniGumb("Pokušaj ponovno", { pokusaj++ }, ikona = "osvjezi", boja = k.vrsta.boja)
                }
                graf != null -> Text("Nema podataka za ovo razdoblje", style = Tip.malo)
                else -> CircularProgressIndicator(Modifier.size(26.dp), color = k.vrsta.boja, strokeWidth = 2.5.dp)
            }
        }
        // Razdoblja
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RazdobljeGrafa.entries.forEach { r ->
                val ok = r == razdoblje
                Text(
                    r.oznaka, color = if (ok) Neon.Tekst else Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ok) bojaGrafa.copy(alpha = .18f) else Color.Transparent)
                        .clickable(onClickLabel = "Razdoblje ${r.oznaka}") { razdoblje = r }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        CijenaPoJedinici(k, vrijednost)
        KljucniPodaci(k)
        k.ocjena?.let { KarticaOcjene(it) }
        Text(
            "Više o ${kratkiSimbol(k.simbol)} na Yahoo Finance ↗",
            color = k.vrsta.boja, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://finance.yahoo.com/quote/" + Uri.encode(k.simbol)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(vertical = 12.dp),
        )
        Napomena()
    }
}

/** Graf cijene: prst (dodir ili vodoravno klizanje) pokazuje vrijednost u zaglavlju; nakon puštanja vraća se trenutna. */
@Composable
private fun GrafUlaganja(tocke: List<TockaGrafa>, pocetna: Double?, boja: Color, odabrani: Int?, onOdabir: (Int?) -> Unit) {
    val vrijednosti = tocke.map { it.vrijednost }
    val min = minOf(vrijednosti.min(), pocetna ?: Double.MAX_VALUE)
    val max = maxOf(vrijednosti.max(), pocetna ?: -Double.MAX_VALUE)
    val raspon = (max - min).takeIf { it > 0 } ?: 1.0
    fun indeks(x: Float, sirina: Float) = ((x / sirina) * tocke.lastIndex).toInt().coerceIn(0, tocke.lastIndex)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Graf cijene, ${tocke.size} točaka" }
            .pointerInput(tocke) {
                detectTapGestures(onPress = {
                    onOdabir(indeks(it.x, size.width.toFloat()))
                    tryAwaitRelease()
                    onOdabir(null)
                })
            }
            .pointerInput(tocke) {
                detectHorizontalDragGestures(
                    onDragStart = { onOdabir(indeks(it.x, size.width.toFloat())) },
                    onDragEnd = { onOdabir(null) },
                    onDragCancel = { onOdabir(null) },
                ) { promjena, _ -> onOdabir(indeks(promjena.position.x, size.width.toFloat())) }
            },
    ) {
        val gore = 10.dp.toPx()
        val dolje = size.height - 10.dp.toPx()
        fun x(i: Int) = size.width * i / tocke.lastIndex
        fun y(v: Double) = (dolje - (dolje - gore) * ((v - min) / raspon)).toFloat()
        // Početna razina (jučerašnje zatvaranje ili početak razdoblja), isprekidano.
        pocetna?.let { p ->
            drawLine(
                Color(0x40FFFFFF), Offset(0f, y(p)), Offset(size.width, y(p)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
        }
        val linija = Path().apply {
            moveTo(x(0), y(vrijednosti[0]))
            for (i in 1..vrijednosti.lastIndex) lineTo(x(i), y(vrijednosti[i]))
        }
        val ispuna = Path().apply { addPath(linija); lineTo(size.width, dolje); lineTo(0f, dolje); close() }
        drawPath(ispuna, Brush.verticalGradient(listOf(boja.copy(alpha = .28f), boja.copy(alpha = 0f)), startY = gore, endY = dolje))
        drawPath(linija, boja.copy(alpha = .3f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(linija, boja, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val i = odabrani ?: return@Canvas
        val px = x(i)
        val py = y(vrijednosti[i])
        drawLine(Color(0x66FFFFFF), Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
        drawCircle(boja.copy(alpha = .3f), 10.dp.toPx(), Offset(px, py))
        drawCircle(Color.White, 4.5.dp.toPx(), Offset(px, py))
    }
}

@Composable
private fun KljucniPodaci(k: Kotacija) {
    val c = { v: Double -> FormatTrzista.cijena(v, k.valuta, k.jePrinos) }
    val stavke = listOfNotNull(
        k.otvaranje?.let { "Otvaranje" to c(it) },
        k.prethodnoZatvaranje?.let { "Prethodno zatvaranje" to c(it) },
        if (k.dnevniMin != null && k.dnevniMax != null) "Dnevni raspon" to "${FormatTrzista.broj(k.dnevniMin, FormatTrzista.decimale(k.dnevniMin))}–${c(k.dnevniMax)}" else null,
        if (k.min52 != null && k.max52 != null) "Raspon 52 tjedna" to "${FormatTrzista.broj(k.min52, FormatTrzista.decimale(k.min52))}–${c(k.max52)}" else null,
        k.trzisnaKap?.let { "Tržišna kapitalizacija" to FormatTrzista.veliki(it) + " " + k.valuta },
        k.volumen?.let { "Promet danas" to FormatTrzista.veliki(it.toDouble()) },
        k.prosjVolumen3m?.let { "Prosj. promet (3 mj.)" to FormatTrzista.veliki(it.toDouble()) },
        k.pe?.let { "P/E omjer" to FormatTrzista.broj(it, 2) },
        k.dividendniPrinos?.takeIf { it > 0 }?.let { "Dividendni prinos" to FormatTrzista.broj(it, 2) + " %" },
        k.burza?.let { "Burza" to it },
    )
    if (stavke.isEmpty()) return
    Kartica(Modifier.fillMaxWidth(), razmak = 12.dp) {
        Text("Ključni podaci", style = Tip.naslovKartice)
        stavke.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEach { (n, v) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(n, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                        // Iznos i valuta u jednom redu: tekst se po potrebi malo smanji.
                        androidx.compose.foundation.text.BasicText(
                            v, maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(color = Neon.Tekst, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                        )
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KarticaOcjene(o: hr.smartbudget.app.data.OcjenaAnaliticara) {
    val boja = when {
        o.vrijednost <= 2.5 -> Neon.Zelena
        o.vrijednost <= 3.5 -> Neon.Jantar
        else -> Neon.Crvena
    }
    Kartica(Modifier.fillMaxWidth(), razmak = 12.dp) {
        Text("Ocjena vanjskih analitičara", style = Tip.naslovKartice)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(o.oznaka, color = boja, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("prosjek ${FormatTrzista.broj(o.vrijednost, 1)} od 5", style = Tip.malo, modifier = Modifier.padding(bottom = 4.dp))
        }
        // Ljestvica 1 (jaka kupnja) … 5 (prodaja) s oznakom prosjeka.
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Box(
                Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Neon.Zelena, Neon.Jantar, Neon.Crvena))),
            )
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * ((o.vrijednost - 1) / 4).toFloat().coerceIn(0f, 1f)
                drawCircle(Color.White, 7.dp.toPx(), Offset(x, size.height / 2))
                drawCircle(boja, 4.dp.toPx(), Offset(x, size.height / 2))
            }
        }
        Row {
            Text("Jaka kupnja", style = Tip.oznaka.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
            Text("Drži", style = Tip.oznaka.copy(fontSize = 11.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("Prodaja", style = Tip.oznaka.copy(fontSize = 11.sp), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Text(
            "Prosjek preporuka analitičara koji prate ovu dionicu (izvor: Yahoo Finance). Nije savjet za ulaganje.",
            style = Tip.malo.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}


/** Roba: cijena preračunata s burzovne jedinice (unca, barel…) na gram, kilogram, litru…, i u osnovnu valutu. */
@Composable
private fun CijenaPoJedinici(k: Kotacija, cijena: Double) {
    val p = hr.smartbudget.app.domain.JediniceRobe.za(k.simbol) ?: return
    val ctx = LocalContext.current
    var tecajevi by remember { mutableStateOf(hr.smartbudget.app.data.Tecajevi.spremljeno(ctx).first) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (tecajevi == null) hr.smartbudget.app.data.Tecajevi.hnb(ctx).onSuccess { tecajevi = it }
    }
    val (kod, _) = hr.smartbudget.app.domain.Portfelj.normalizirajValutu(k.valuta)
    val osnovna = osnovnaValuta()
    val uOsnovnu = if (kod == osnovna.kod) null
        else hr.smartbudget.app.data.Tecajevi.pretvori(1.0, hr.smartbudget.app.domain.Valute.po(kod), osnovna, tecajevi, null)
    Kartica(Modifier.fillMaxWidth(), razmak = 10.dp) {
        Text("Cijena po jedinici", style = Tip.naslovKartice)
        Text("Burza kotira po jedinici: ${p.izvorna}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        hr.smartbudget.app.domain.JediniceRobe.cijene(k.simbol, cijena, k.valuta).forEach { (n, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, style = Tip.tijelo, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(FormatTrzista.cijena(v, kod), style = Tip.iznos.copy(fontSize = 16.sp), color = Neon.Tekst)
                    uOsnovnu?.let { Text("≈ " + hr.smartbudget.app.domain.Valute.iznos(v * it, osnovna), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal)) }
                }
            }
        }
        if (uOsnovnu != null) Text("Preračun u ${osnovna.kod} po srednjem tečaju HNB-a.", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
    }
}
