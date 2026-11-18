package hr.smartbudget.app.ui.screens

import hr.smartbudget.app.ui.skrivaStednju
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.data.UplataCilja
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.StanjeNovcanika
import hr.smartbudget.app.domain.Valuta
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.domain.uplatePoMjesecima
import hr.smartbudget.app.domain.vrijednostPortfelja
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KriptoPovijest
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.LinijskiGraf
import hr.smartbudget.app.ui.StanjeTecajeva
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.LocalDate

private val bojeUdjela = listOf(Neon.Zelena, Neon.Cijan, Neon.LjubicastaSvijetla, Neon.Jantar, Color(0xFF4D8BFF), Color(0xFFFF7EB6), Color(0xFF9BE15D))

@Composable
private fun NaslovKartice(naslov: String, podnaslov: String, desno: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(naslov, style = Tip.naslovKartice)
            Text(podnaslov, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        desno()
    }
}

/**
 * Pregled štednje: ukupno (ciljevi + novčanici, preračunato u osnovnu valutu), sastav po valutama
 * i neto uplate po mjesecima. Prikazuje se samo kad korisnik štedi.
 */
@Composable
fun StednjaAnalitikaKartica(
    ciljevi: List<CiljStednje>,
    uplate: List<UplataCilja>,
    novcanici: List<StanjeNovcanika>,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
) {
    if (ciljevi.isEmpty() && novcanici.isEmpty()) return
    val trebaTecaj = novcanici.isNotEmpty() || uplate.any { it.valuta != null }
    LaunchedEffect(trebaTecaj) { if (trebaTecaj && t.hnb == null && t.kripto == null) onOsvjezi() }
    val osnovna = osnovnaValuta()
    fun uOsnovnu(j: Long, v: Valuta): Double? = if (v.kod == osnovna.kod) Valute.uBroj(j, v) else t.pretvori(j, v, osnovna)

    // Sastav: ciljevi u osnovnoj valuti + svaka valuta novčanika
    val dijelovi = buildList {
        val c = ciljevi.sumOf { hr.smartbudget.app.domain.ustedjenoCilja(it, uplate, ::uOsnovnu).centi }
        if (c > 0) add("Ciljevi štednje" to c / 100.0)
        novcanici.groupBy { it.valuta }.forEach { (v, l) ->
            uOsnovnu(l.sumOf { it.stanje }, v)?.takeIf { it > 0 }?.let { add((if (v.kod == osnovna.kod) "Novčanici ${v.kod}" else v.kod) to it) }
        }
    }.sortedByDescending { it.second }
    val ukupno = dijelovi.sumOf { it.second }
    val mjesecno = uplatePoMjesecima(uplate, novcanici, ::uOsnovnu)
    val prosjek = mjesecno.map { it.second }.average()

    Kartica(razmak = 14.dp) {
        NaslovKartice("Štednja", "Ciljevi i novčanici, preračunato u ${osnovna.kod}")
        Text(Valute.iznos(ukupno, osnovna), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena, modifier = Modifier.skrivaStednju())

        // Sastav kao jedna složena traka s legendom
        if (dijelovi.size > 1) {
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                dijelovi.forEachIndexed { i, (_, v) ->
                    Box(Modifier.weight((v / ukupno).toFloat().coerceAtLeast(.01f)).fillMaxHeight().background(bojeUdjela[i % bojeUdjela.size]))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dijelovi.forEachIndexed { i, (n, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(bojeUdjela[i % bojeUdjela.size]))
                    Text(n, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Novac.postotak(v * 100 / ukupno.coerceAtLeast(.01)), style = Tip.oznaka, modifier = Modifier.padding(end = 10.dp))
                    Text(Valute.iznos(v, osnovna), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, maxLines = 1)
                }
            }
        }

        // Neto uplate po mjesecima (stupci), s prosjekom
        HorizontalDivider(color = Neon.RubSlab)
        Text("Uplate u štednju po mjesecima", style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        val maks = mjesecno.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            mjesecno.forEachIndexed { i, (m, v) ->
                val zadnji = i == mjesecno.lastIndex
                val boja = if (v < 0) Neon.Jantar else if (zadnji) Neon.Zelena else Neon.Cijan
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (v != 0.0) Text(Novac.cijeli(kotlin.math.round(v).toLong()), fontSize = 10.sp, color = if (zadnji) Neon.Zelena else Neon.TekstSiv, maxLines = 1)
                    Box(
                        Modifier.padding(top = 3.dp).fillMaxWidth()
                            .fillMaxHeight((kotlin.math.abs(v) / maks).toFloat().coerceIn(.03f, 1f) * .72f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.verticalGradient(listOf(boja, boja.copy(alpha = .4f)))),
                    )
                    Text(HrDatum.mjeseciKratko[m.monthValue - 1], style = Tip.oznaka.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            buildString {
                append("U prosjeku odvajaš ${Valute.iznos(prosjek, osnovna)} mjesečno.")
                val ovaj = mjesecno.last().second
                if (ovaj > prosjek && ovaj > 0) append(" Ovaj mjesec iznad prosjeka, bravo!")
                else if (ovaj <= 0) append(" Ovaj mjesec još nema uplata.")
            },
            color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (novcanici.any { it.valuta.kod != osnovna.kod }) {
            Text("Strane valute i kriptovalute preračunate su po današnjem tečaju.", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        }
    }
}

/** Kretanje vrijednosti kripto novčanika kroz razdoblje, promjene i udjeli po kriptovaluti. */
@Composable
fun KriptoAnalitikaKartica(
    novcanici: List<StanjeNovcanika>,
    t: StanjeTecajeva,
    povijest: KriptoPovijest,
    onOsvjeziTecajeve: () -> Unit,
    onPovijest: (Int) -> Unit,
) {
    val kripto = novcanici.filter { it.valuta.kripto }
    if (kripto.isEmpty()) return
    val razdoblja = listOf(30, 90, 365)
    var odabrano by rememberSaveable { mutableIntStateOf(1) }
    val dana = razdoblja[odabrano]
    LaunchedEffect(dana, kripto.map { it.valuta.kod }.toSet()) {
        if (povijest.dana != dana || povijest.cijene.keys != kripto.map { it.valuta.kod }.toSet()) onPovijest(dana)
        if (t.kripto == null) onOsvjeziTecajeve()
    }
    val osnovna = osnovnaValuta()
    val eur = Valute.po("EUR")
    // Graf je u eurima (CoinGecko); za drugu osnovnu valutu preračun po današnjem srednjem tečaju.
    val faktor = if (osnovna.kod == "EUR") 1.0 else hr.smartbudget.app.data.Tecajevi.pretvori(1.0, eur, osnovna, t.hnb, t.kripto) ?: 1.0
    val danas = LocalDate.now()
    // Trenutno stanje vrednovano po cijenama kroz razdoblje (kao u mjenjačnicama): kretanje bez skokova od uplata.
    // Dodir na kriptovalutu u popisu prikazuje njen graf; gumb za reset vraća ukupno.
    var odabranaKod by rememberSaveable { mutableStateOf<String?>(null) }
    val odabrana = kripto.firstOrNull { it.valuta.kod == odabranaKod }
    val tocke = remember(povijest, kripto, faktor, odabrana) {
        val trenutno = (odabrana?.let { listOf(it) } ?: kripto).map { it.copy(promjene = listOf(hr.smartbudget.app.data.PromjenaNovcanika(novcanikId = it.novcanik.id, jedinice = it.stanje, epochDan = 0))) }
        vrijednostPortfelja(trenutno, povijest.cijene, danas.minusDays(dana.toLong() - 1), danas).map { it.first to it.second * faktor }
    }
    val vrijednosti = kripto.map { it to t.pretvori(it.stanje, it.valuta, osnovna) }
    val ukupno = vrijednosti.sumOf { it.second ?: 0.0 }
    val promjena24 = vrijednosti.sumOf { (s, v) ->
        val p = t.kripto?.promjena24h?.get(s.valuta.kod) ?: 0.0
        (v ?: 0.0) - (v ?: 0.0) / (1 + p / 100)
    }
    val ulozeno = kripto.takeIf { l -> l.all { it.ulozenoPotpuno && it.ulozeno != null } }?.sumOf { it.ulozeno!! }?.div(100.0)

    Kartica(razmak = 14.dp) {
        NaslovKartice("Kriptovalute", "Vrijednost trenutnog stanja kroz vrijeme · CoinGecko") {
            KruzniGumb("osvjezi", "Osvježi cijene", { onOsvjeziTecajeve(); onPovijest(dana) }, velicina = 38.dp, boja = Neon.LjubicastaSvijetla)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Valute.iznos(ukupno, osnovna), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.LjubicastaTekst, modifier = Modifier.skrivaStednju())
            if (t.kripto != null && ukupno > 0) {
                val p = promjena24 * 100 / (ukupno - promjena24).coerceAtLeast(.01)
                Text(
                    (if (promjena24 >= 0) "▲ " else "▼ ") + Novac.postotak(kotlin.math.abs(p)) + " 24h",
                    color = if (promjena24 >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        // Dobit/gubitak u odnosu na uloženo
        ulozeno?.takeIf { it > 0 }?.let { ul ->
            val d = ukupno - ul
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlocicaBroja("Uloženo", Valute.iznos(ul, osnovna), Neon.Tekst, Modifier.weight(1f))
                PlocicaBroja(if (d >= 0) "Dobit" else "Gubitak", Valute.iznos(kotlin.math.abs(d), osnovna), if (d >= 0) Neon.Zelena else Neon.Crvena, Modifier.weight(1f))
                PlocicaBroja("Prinos", (if (d >= 0) "+" else Novac.MINUS) + Novac.postotak(kotlin.math.abs(d) * 100 / ul), if (d >= 0) Neon.Zelena else Neon.Crvena, Modifier.weight(1f))
            }
        }
        Segmenti(listOf("30 dana", "90 dana", "1 godina"), odabrano, { odabrano = it }, boje = List(3) { Neon.LjubicastaSvijetla })
        val bojaGrafa = if (odabrana != null) Neon.Cijan else Neon.LjubicastaSvijetla
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (odabrana != null) PlocicaValute(odabrana.valuta, 34.dp)
            Text(
                odabrana?.let { "${it.valuta.naziv} · ${Valute.iznos(it.stanje, it.valuta)}" } ?: "Ukupno novčanik",
                style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).padding(start = if (odabrana != null) 8.dp else 0.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (odabrana != null) KruzniGumb("restore", "Prikaži ukupno", { odabranaKod = null }, velicina = 32.dp, boja = Neon.Cijan)
        }
        // Ako za neku valutu povijest nije stigla, ukupni graf bi bio nepotpun: to se jasno kaže.
        val bezPovijesti = (odabrana?.let { listOf(it) } ?: kripto).filter { povijest.cijene[it.valuta.kod] == null }.map { it.valuta.kod }
        when {
            !povijest.ucitava && bezPovijesti.isNotEmpty() && tocke.size >= 2 -> Text(
                "Povijest cijena za ${bezPovijesti.joinToString(", ")} trenutno nije dostupna (CoinGecko ograničava broj upita), pa graf nije potpun. Pokušajte osvježiti za minutu.",
                color = Neon.Jantar, fontSize = 12.sp, lineHeight = 16.sp,
            )
            else -> {}
        }
        when {
            tocke.size >= 2 -> {
                LinijskiGraf(tocke, bojaGrafa, { Valute.iznos(it, osnovna) })
                val prvi = tocke.firstOrNull { it.second > 0 }
                if (prvi != null) {
                    val z = tocke.last().second
                    val p = (z - prvi.second) * 100 / prvi.second
                    Text(
                        "Od ${HrDatum.puni(prvi.first)} vrijednost " + (odabrana?.let { "vašeg ${it.valuta.kod}" } ?: "vaših kriptovaluta") + " " + (if (p >= 0) "porasla je" else "pala je") +
                            " ${Novac.postotak(kotlin.math.abs(p))} (${if (z - prvi.second >= 0) "+" else Novac.MINUS}${Valute.iznos(kotlin.math.abs(z - prvi.second), osnovna)}).",
                        color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            povijest.ucitava -> Text("Dohvaćam povijest cijena…", style = Tip.malo)
            else -> Text(povijest.greska ?: "Graf će se pokazati kad bude dostupna povijest cijena.", style = Tip.malo)
        }
        // Po kriptovaluti (dodir = graf te valute)
        HorizontalDivider(color = Neon.RubSlab)
        if (kripto.size > 1) Text("Dodirnite kriptovalutu za njen graf", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        vrijednosti.sortedByDescending { it.second ?: 0.0 }.forEach { (s, v) ->
            val jeOdabrana = s.valuta.kod == odabranaKod
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (jeOdabrana) Neon.Cijan.copy(alpha = .1f) else Color.Transparent)
                    .clickable(onClickLabel = "Prikaži graf") { odabranaKod = if (jeOdabrana) null else s.valuta.kod }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlocicaValute(s.valuta, 34.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.valuta.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1)
                        Text(Valute.iznos(s.stanje, s.valuta), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(v?.let { Valute.iznos(it, osnovna) } ?: "—", style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, maxLines = 1)
                        t.kripto?.promjena24h?.get(s.valuta.kod)?.let { Promjena24h(it) }
                    }
                }
                if (kripto.size > 1 && v != null && ukupno > 0) {
                    TrakaNapretka((v / ukupno).toFloat(), Brush.horizontalGradient(listOf(Neon.LjubicastaSvijetla, Neon.Cijan)), Neon.LjubicastaSvijetla, visina = 4.dp)
                }
            }
        }
        IzvorTecajeva(t, kripto = true, fiat = osnovna.kod != "EUR")
    }
}

@Composable
private fun PlocicaBroja(naziv: String, vrijednost: String, boja: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(vrijednost, color = boja, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        Text(naziv, style = Tip.oznaka.copy(fontSize = 11.sp), maxLines = 1)
    }
}

/**
 * Analitika ciljeva štednje: ukupni napredak, kretanje ušteđenog kroz vrijeme i za svaki cilj
 * napredak u odnosu na plan (oznaka na traci = koliko je po planu trebalo do danas) i procjena.
 */
@Composable
fun CiljeviAnalitikaKartica(
    ciljevi: List<CiljStednje>,
    uplate: List<UplataCilja>,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
) {
    if (ciljevi.isEmpty()) return
    val trebaTecaj = uplate.any { it.valuta != null }
    LaunchedEffect(trebaTecaj) { if (trebaTecaj && t.hnb == null && t.kripto == null) onOsvjezi() }
    val osnovna = osnovnaValuta()
    val uOsnovnu: (Long, Valuta) -> Double? = { j, v -> uOsnovnuValutu(j, v, t) }
    val stavke = ciljevi.map { c ->
        val ust = hr.smartbudget.app.domain.ustedjenoCilja(c, uplate, uOsnovnu)
        Triple(c, ust, hr.smartbudget.app.domain.analizirajCilj(c.copy(ustedjenoCenti = ust.centi)))
    }
    val ukupnoCilj = ciljevi.sumOf { it.ciljCenti }
    val ukupnoUst = stavke.sumOf { it.second.centi }
    val udio = if (ukupnoCilj > 0) (ukupnoUst.toFloat() / ukupnoCilj).coerceIn(0f, 1f) else 0f

    // Kumulativno ušteđeno na svim ciljevima po danima (strane valute po današnjem tečaju)
    val ids = ciljevi.map { it.id }.toSet()
    val tocke = remember(uplate, ciljevi, t) {
        val relevantne = uplate.filter { it.ciljId in ids }
        if (relevantne.isEmpty()) emptyList() else {
            val poDanu = relevantne.groupBy { it.epochDan }.mapValues { (_, l) ->
                l.sumOf { u -> if (u.valuta == null) u.iznosCenti / 100.0 else uOsnovnu(u.iznosCenti, Valute.po(u.valuta)) ?: 0.0 }
            }
            val od = LocalDate.ofEpochDay(poDanu.keys.min()).minusDays(1)
            val danas = LocalDate.now()
            var zbroj = 0.0
            generateSequence(od) { it.plusDays(1) }.takeWhile { !it.isAfter(danas) }.map { d ->
                zbroj += poDanu[d.toEpochDay()] ?: 0.0
                d to zbroj
            }.toList()
        }
    }

    Kartica(razmak = 14.dp) {
        NaslovKartice("Ciljevi štednje", "Napredak prema ciljevima i u odnosu na plan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlocicaBroja("Ušteđeno", Valute.iznos(ukupnoUst / 100.0, osnovna), Neon.Zelena, Modifier.weight(1f).skrivaStednju())
            PlocicaBroja("Ukupni cilj", Valute.iznos(ukupnoCilj / 100.0, osnovna), Neon.Tekst, Modifier.weight(1f))
            PlocicaBroja("Ostvareno", Novac.postotak(udio * 100.0, 0), Neon.Cijan, Modifier.weight(1f))
        }
        TrakaNapretka(udio, ispunaNapretka(udio), bojaNapretka(udio), visina = 8.dp)

        if (tocke.size >= 3) {
            Text("Ušteđeno kroz vrijeme", style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            LinijskiGraf(tocke, Neon.Zelena, { Valute.iznos(it, osnovna) }, visina = 140.dp)
        } else {
            Text("Graf kretanja štednje pojavit će se nakon prvih uplata na ciljeve.", style = Tip.malo)
        }

        HorizontalDivider(color = Neon.RubSlab)
        stavke.forEach { (c, ust, a) ->
            val boja = hr.smartbudget.app.ui.theme.hexBoja(c.bojaHex)
            val plan = a.ocekivanoDoSad?.let { if (c.ciljCenti > 0) it.toFloat() / c.ciljCenti else null }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Novac.eur(ust.centi)} / ${Novac.eur(c.ciljCenti)}",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(Novac.postotak(a.udio * 100.0, 0), color = boja, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(44.dp))
                }
                TrakaNapretka(a.udio, ispunaNapretka(a.udio), bojaNapretka(a.udio), visina = 7.dp, marker = plan)
                val (tekst, b) = when (a.status) {
                    hr.smartbudget.app.domain.StatusCilja.DOSEGNUT -> "Dosegnut" to Neon.Zelena
                    hr.smartbudget.app.domain.StatusCilja.ISPRED_PLANA -> "Ispred plana ${Novac.eur(ust.centi - (a.ocekivanoDoSad ?: 0))}" to Neon.Zelena
                    hr.smartbudget.app.domain.StatusCilja.NA_PUTU -> "Na putu" to Neon.Cijan
                    hr.smartbudget.app.domain.StatusCilja.KASNI -> "Kasni ${Novac.eur((a.ocekivanoDoSad ?: 0) - ust.centi)}" to Neon.Jantar
                    hr.smartbudget.app.domain.StatusCilja.ISTEKAO -> "Rok prošao" to Neon.Crvena
                    hr.smartbudget.app.domain.StatusCilja.BEZ_ROKA -> "Bez roka" to Neon.TekstSiv
                }
                val dodatno = when {
                    a.status == hr.smartbudget.app.domain.StatusCilja.DOSEGNUT -> null
                    a.procjena != null -> "procjena: ${HrDatum.puni(a.procjena)}"
                    a.mjesecno != null && a.mjesecno > 0 -> "treba ${Novac.eur(a.mjesecno)}/mj."
                    else -> "još ${Novac.eur(a.ostalo)}"
                }
                Text(
                    tekst + (dodatno?.let { " · $it" } ?: ""),
                    color = b, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stavke.any { it.third.ocekivanoDoSad != null }) {
            Text("Bijela oznaka na traci pokazuje koliko je po planu trebalo biti ušteđeno do danas.", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        }
    }
}

/** Boja napretka prema cilju: crvena na početku, preko jantarne do zelene na cilju. */
fun bojaNapretka(udio: Float): Color {
    val t = udio.coerceIn(0f, 1f)
    return if (t < .5f) androidx.compose.ui.graphics.lerp(Neon.Crvena, Neon.Jantar, t * 2)
    else androidx.compose.ui.graphics.lerp(Neon.Jantar, Neon.Zelena, (t - .5f) * 2)
}

/** Ispuna trake: gradijent od crvene do boje trenutnog napretka (bliže cilju = zelenije). */
fun ispunaNapretka(udio: Float): Brush {
    val t = udio.coerceIn(0f, 1f)
    return Brush.horizontalGradient(listOf(Neon.Crvena, bojaNapretka(t / 2), bojaNapretka(t)))
}
