package hr.smartbudget.app.ui.screens

import hr.smartbudget.app.ui.skrivaStednju
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.data.Novcanik
import hr.smartbudget.app.data.PromjenaNovcanika
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.StanjeNovcanika
import hr.smartbudget.app.domain.Valuta
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.StanjeTecajeva
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Osnovna valuta aplikacije (iz Postavki). */
fun osnovnaValuta(): Valuta = Valute.po(Novac.kodValute)

private val satMinuta = DateTimeFormatter.ofPattern("HH:mm")
private fun vrijeme(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(satMinuta)

/** Izvor i svježina tečajeva, da korisnik zna po čemu je preračunato. */
@Composable
fun IzvorTecajeva(t: StanjeTecajeva, kripto: Boolean, fiat: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            t.ucitava -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Neon.Cijan, strokeWidth = 2.dp)
                Text("Dohvaćam svježe tečajeve…", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            else -> {
                if (fiat) t.hnb?.let {
                    Text(
                        (if (it.izvor == "ECB") "Srednji tečaj za ${HrDatum.puni(it.datum)} (HNB nedostupan, preuzet jednaki referentni tečaj ECB-a)"
                        else "Srednji tečaj HNB-a, tečajnica br. ${it.broj} za ${HrDatum.puni(it.datum)}") +
                            if (it.izSpremljenog) " (spremljeno, bez interneta)" else "",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
                    )
                }
                if (kripto) t.kripto?.let {
                    Text(
                        "Kriptovalute: CoinGecko, cijena u ${vrijeme(it.dohvaceno)}" + if (it.izSpremljenog) " (spremljeno, bez interneta)" else "",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
                    )
                }
                t.greska?.let { Text(it, color = Neon.Jantar, fontSize = 11.sp) }
            }
        }
    }
}

/** Pločica promjene cijene kriptovalute u 24 h. */
@Composable
fun Promjena24h(p: Double) {
    val boja = if (p >= 0) Neon.Zelena else Neon.Crvena
    Oznaka((if (p >= 0) "▲ " else "▼ ") + Novac.postotak(kotlin.math.abs(p)) + " 24h", boja)
}

@Composable
fun NovcaniciKartica(
    novcanici: List<StanjeNovcanika>,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
    onNovi: () -> Unit,
    onOtvori: (StanjeNovcanika) -> Unit,
    onKalkulator: () -> Unit,
    /** Dijeljenje odabranih (ili svih) novčanika kao teksta. */
    onPodijeli: (List<StanjeNovcanika>) -> Unit = {},
    /** Brisanje novčanika (nakon potvrde). */
    onObrisi: (List<Novcanik>) -> Unit = {},
) {
    // Svaki put kad se kartica pokaže, tečajevi se osvježe.
    LaunchedEffect(novcanici.isNotEmpty()) { if (novcanici.isNotEmpty()) onOsvjezi() }
    val osnovna = osnovnaValuta()
    // Višestruki odabir (dugi pritisak): id-jevi odabranih novčanika
    var odabrani by remember { mutableStateOf(setOf<Long>()) }
    val odabraneStavke = novcanici.filter { it.novcanik.id in odabrani }
    val uOdabiru = odabraneStavke.isNotEmpty()
    var zaBrisanje by remember { mutableStateOf<List<StanjeNovcanika>?>(null) }
    androidx.activity.compose.BackHandler(uOdabiru) { odabrani = emptySet() }
    fun prebaci(id: Long) { odabrani = if (id in odabrani) odabrani - id else odabrani + id }
    zaBrisanje?.let { l ->
        PotvrdaBrisanja(
            if (l.size == 1) "Obrisati novčanik?" else "Obrisati ${l.size} novčanika?",
            l.joinToString(", ") { it.novcanik.naziv } + " i sva njihova povijest bit će obrisani.",
            { zaBrisanje = null },
        ) { onObrisi(l.map { it.novcanik }); odabrani = emptySet(); zaBrisanje = null }
    }
    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Štednja u valutama", style = Tip.naslovKartice)
                Text("Strane valute i kriptovalute", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            if (novcanici.isNotEmpty()) {
                KruzniGumb("share", "Podijeli štednju u valutama", { onPodijeli(novcanici) }, velicina = 40.dp, boja = Neon.Cijan)
                Spacer(Modifier.width(8.dp))
            }
            KruzniGumb("kalkulator", "Kalkulator valuta", onKalkulator, velicina = 40.dp, boja = Neon.Cijan)
            Spacer(Modifier.width(8.dp))
            KruzniGumb("plus", "Novi novčanik", onNovi, velicina = 40.dp, boja = Neon.Zelena)
        }
        if (novcanici.isEmpty()) {
            Text("Dodajte novčanik npr. za dolare, franke ili bitcoin. Vrijednost se preračunava po srednjem tečaju HNB-a i cijenama s CoinGecka.", style = Tip.malo)
            return@Kartica
        }
        if (uOdabiru) {
            TrakaOdabira(
                odabraneStavke.size, odabraneStavke.size == novcanici.size,
                { odabrani = if (odabraneStavke.size == novcanici.size) emptySet() else novcanici.map { it.novcanik.id }.toSet() },
                { onPodijeli(odabraneStavke) }, { zaBrisanje = odabraneStavke }, { odabrani = emptySet() }, Modifier,
            )
        } else {
            Text("Povuci ulijevo za brisanje, udesno za dijeljenje · dugi pritisak za odabir više", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        }
        var ukupno = 0.0
        var sviPreracunati = true
        novcanici.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = Neon.RubSlab)
            val preracun = if (s.valuta.kod == osnovna.kod) null else t.pretvori(s.stanje, s.valuta, osnovna)
            if (s.valuta.kod == osnovna.kod) ukupno += Valute.uBroj(s.stanje, s.valuta) else if (preracun != null) ukupno += preracun else sviPreracunati = false
            androidx.compose.runtime.key(s.novcanik.id) {
            PrevlacivaStavka(
                omoguceno = !uOdabiru, onPodijeli = { onPodijeli(listOf(s)) }, onObrisi = { zaBrisanje = listOf(s) }, brisanjeUlijevo = true,
            ) {
            RedSOdabirom(
                if (uOdabiru) s.novcanik.id in odabrani else null, "Otvori novčanik",
                onClick = { if (uOdabiru) prebaci(s.novcanik.id) else onOtvori(s) }, onDugi = { prebaci(s.novcanik.id) },
            ) {
                PlocicaValute(s.valuta)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.novcanik.naziv, style = Tip.tijelo.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            s.valuta.kod == osnovna.kod -> s.valuta.naziv
                            preracun != null -> "≈ " + Valute.iznos(preracun, osnovna)
                            t.ucitava -> "preračunavam…"
                            else -> "nema tečaja"
                        } + (s.novcanik.rokEpochDan?.let { " · " + hr.smartbudget.app.domain.Rokovi.opis(LocalDate.ofEpochDay(it)) } ?: ""),
                        style = Tip.malo, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Valute.iznos(s.stanje, s.valuta), style = Tip.iznos.copy(fontSize = 15.sp), color = Neon.Tekst, maxLines = 1, softWrap = false)
                    if (s.valuta.kripto) t.kripto?.promjena24h?.get(s.valuta.kod)?.let { Promjena24h(it) }
                }
            }
            }
            }
        }
        HorizontalDivider(color = Neon.RubSlab)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ukupno", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text((if (sviPreracunati) "≈ " else "≥ ") + Valute.iznos(ukupno, osnovna), style = Tip.iznos.copy(fontSize = 17.sp), color = Neon.Zelena)
        }
        IzvorTecajeva(t, kripto = novcanici.any { it.valuta.kripto }, fiat = novcanici.any { !it.valuta.kripto && it.valuta.kod != "EUR" } || osnovna.kod != "EUR")
    }
}

/** Novi novčanik (null) ili postojeći: stanje, uplata/isplata, povijest, uređivanje naziva i brisanje. */
@Composable
fun NovcanikSheet(
    s: StanjeNovcanika?,
    t: StanjeTecajeva,
    onSpremi: (Novcanik) -> Unit,
    onObrisi: (Novcanik) -> Unit,
    onPromjena: (PromjenaNovcanika) -> Unit,
    onObrisiPromjenu: (PromjenaNovcanika) -> Unit,
    /** Nazivi računa na koje može ići novac od isplate. */
    racuni: List<String> = emptyList(),
    /** Isplata s opcionalnim priljevom na račun: promjena, račun (null = ne na račun), iznos u centima, opis. */
    onIsplata: (PromjenaNovcanika, String?, Long?, String) -> Unit = { p, _, _, _ -> onPromjena(p) },
    /** Uređivanje uplate ili isplate (iznos, plaćeno, datum, napomena). */
    onUrediPromjenu: (PromjenaNovcanika) -> Unit = {},
    onZatvori: () -> Unit,
) {
    val osnovna = osnovnaValuta()
    var valuta by remember { mutableStateOf(s?.valuta ?: Valute.po("USD")) }
    var racun by remember { mutableStateOf(racuni.firstOrNull { it == hr.smartbudget.app.data.NacinPlacanja.TEKUCI } ?: racuni.firstOrNull()) }
    var iznosNaRacun by remember { mutableStateOf("") }
    var biramRok by remember { mutableStateOf(false) }
    var uredujemPromjenu by remember { mutableStateOf<PromjenaNovcanika?>(null) }
    var naziv by remember { mutableStateOf(s?.novcanik?.naziv ?: "") }
    var uredujem by remember { mutableStateOf(s == null) }
    var uplata by remember { mutableStateOf(true) }
    var iznos by remember { mutableStateOf("") }
    var placeno by remember { mutableStateOf("") }
    var napomena by remember { mutableStateOf("") }
    var greska by remember { mutableStateOf<String?>(null) }
    var potvrda by remember { mutableStateOf(false) }
    var brisemPromjenu by remember { mutableStateOf<PromjenaNovcanika?>(null) }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(if (s == null) "Novi novčanik" else s.novcanik.naziv, onZatvori)
        if (s != null) {
            // Stanje i preračun
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bojaValute(s.valuta).copy(alpha = .08f))
                    .border(1.dp, bojaValute(s.valuta).copy(alpha = .25f), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlocicaValute(s.valuta, 36.dp)
                    Text(s.valuta.naziv, style = Tip.oznaka, modifier = Modifier.weight(1f))
                    if (s.valuta.kripto) t.kripto?.promjena24h?.get(s.valuta.kod)?.let { Promjena24h(it) }
                }
                Text(Valute.iznos(s.stanje, s.valuta), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, maxLines = 1)
                if (s.valuta.kod != osnovna.kod) {
                    t.pretvori(s.stanje, s.valuta, osnovna)?.let { v ->
                        Text("≈ " + Valute.iznos(v, osnovna), style = Tip.iznos.copy(fontSize = 16.sp), color = Neon.TekstSvijetliSiv)
                        if (s.valuta.kripto && s.ulozeno != null && s.ulozenoPotpuno) {
                            val ul = s.ulozeno!! / 100.0
                            val dobit = v - ul
                            Text(
                                "Uloženo ${Valute.iznos(ul, osnovna)} · " + (if (dobit >= 0) "dobit " else "gubitak ") + Valute.iznos(kotlin.math.abs(dobit), osnovna) +
                                    if (ul > 0) " (${if (dobit >= 0) "+" else Novac.MINUS}${Novac.postotak(kotlin.math.abs(dobit) * 100 / ul)})" else "",
                                color = if (dobit >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp,
                            )
                        }
                    }
                    IzvorTecajeva(t, kripto = s.valuta.kripto, fiat = !s.valuta.kripto || osnovna.kod != "EUR")
                }
            }
            // Rok štednje (podsjetnici 30, 15, 5, 1 dan prije i na dan isteka)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.novcanik.rokEpochDan?.let { "Rok: " + hr.smartbudget.app.domain.Rokovi.opis(LocalDate.ofEpochDay(it)) } ?: "Rok štednje nije postavljen",
                    style = Tip.malo, modifier = Modifier.weight(1f),
                )
                Text(
                    if (s.novcanik.rokEpochDan == null) "Postavi" else "Promijeni", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { biramRok = true }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
                if (s.novcanik.rokEpochDan != null) Text(
                    "Ukloni", color = Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSpremi(s.novcanik.copy(rokEpochDan = null)) }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            // Uplata ili isplata
            Segmenti(listOf("Uplata", "Isplata"), if (uplata) 0 else 1, { uplata = it == 0; greska = null }, boje = listOf(Neon.Zelena, Neon.Jantar))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OznakaPolja("Iznos (${s.valuta.kod})")
                NeonPolje(
                    iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(),
                    placeholder = if (s.valuta.kripto) "0,00000000" else "0,00",
                    uCentima = true, decimale = s.valuta.decimale,
                    stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (uplata) Neon.Zelena else Neon.Jantar, fontFeatureSettings = "tnum"),
                )
            }
            if (uplata && s.valuta.kripto) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OznakaPolja("Plaćeno (${osnovna.kod}, opcionalno, za izračun dobiti)")
                    NeonPolje(placeno, { placeno = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                }
            }
            if (!uplata) {
                OdredisteNovca(racuni, racun) { racun = it }
                if (racun != null && s.valuta.kod != osnovna.kod) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OznakaPolja("Iznos na računu (${osnovna.kod}, prazno = po današnjem tečaju)")
                        NeonPolje(iznosNaRacun, { iznosNaRacun = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                    }
                }
            }
            NeonPolje(napomena, { napomena = it }, Modifier.fillMaxWidth(), placeholder = "Napomena (opcionalno)")
            greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
            PrimarniGumb(if (uplata) "Dodaj uplatu" else "Dodaj isplatu", onClick = {
                val j = Valute.parsiraj(iznos, s.valuta)
                when {
                    j == null || j <= 0 -> greska = if (s.valuta.kripto) "Unesite iznos (najviše 8 decimala)." else "Unesite iznos."
                    !uplata && j > s.stanje -> greska = "U novčaniku je samo ${Valute.iznos(s.stanje, s.valuta)}."
                    else -> {
                        val promjena = PromjenaNovcanika(
                            novcanikId = s.novcanik.id, jedinice = if (uplata) j else -j, epochDan = LocalDate.now().toEpochDay(),
                            ulozenoCenti = if (uplata && s.valuta.kripto) Novac.parsiraj(placeno)?.takeIf { it > 0 } else null,
                            napomena = napomena.trim(),
                        )
                        if (uplata) onPromjena(promjena)
                        else {
                            // Iznos u osnovnoj valuti: upisani ili po današnjem tečaju
                            val naRacun = Novac.parsiraj(iznosNaRacun)?.takeIf { it > 0 }
                                ?: (if (s.valuta.kod == osnovna.kod) j else t.pretvori(j, s.valuta, osnovna)?.let { kotlin.math.round(it * 100).toLong() })
                            onIsplata(promjena, racun, naRacun, "Isplata: ${s.novcanik.naziv}")
                        }
                        iznos = ""; placeno = ""; napomena = ""; iznosNaRacun = ""
                    }
                }
            }, ikona = "check")
            // Povijest
            if (s.promjene.isNotEmpty()) {
                Column {
                    OznakaPolja("Povijest · dodirnite za uređivanje")
                    s.promjene.take(15).forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi promjenu") { uredujemPromjenu = p }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(HrDatum.puni(LocalDate.ofEpochDay(p.epochDan)), style = Tip.tijelo.copy(fontSize = 14.sp))
                                val dodatno = listOfNotNull(p.napomena.takeIf { it.isNotBlank() }, p.ulozenoCenti?.let { "plaćeno ${Novac.eur(it)}" })
                                if (dodatno.isNotEmpty()) Text(dodatno.joinToString(" · "), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
                            }
                            Text(
                                (if (p.jedinice >= 0) "+" else "") + Valute.iznos(p.jedinice, s.valuta),
                                style = Tip.iznos.copy(fontSize = 14.sp), color = if (p.jedinice >= 0) Neon.Zelena else Neon.Jantar,
                            )
                        }
                    }
                    if (s.promjene.size > 15) Text("i još ${s.promjene.size - 15} starijih", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                }
            }
        }
        // Naziv i valuta (novi) ili uređivanje naziva
        if (uredujem) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OznakaPolja("Naziv")
                NeonPolje(naziv, { naziv = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "npr. Dolari za putovanje")
            }
            if (s == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OznakaPolja("Valuta")
                    GumbValute(valuta, Modifier.fillMaxWidth()) { valuta = it; if (naziv.isBlank()) naziv = it.naziv }
                }
                Text(
                    if (valuta.kripto) "Vrijednost u ${osnovna.kod} računa se po cijeni s CoinGecka, osvježenoj pri svakom otvaranju."
                    else "Vrijednost u ${osnovna.kod} računa se po srednjem tečaju HNB-a, osvježenom pri svakom otvaranju.",
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                )
            }
            greska?.let { if (s == null) Text(it, color = Neon.Crvena, fontSize = 12.sp) }
            PrimarniGumb(if (s == null) "Dodaj novčanik" else "Spremi naziv", onClick = {
                if (naziv.isBlank()) greska = "Unesite naziv."
                else {
                    onSpremi(s?.novcanik?.copy(naziv = naziv.trim()) ?: Novcanik(naziv = naziv.trim(), valuta = valuta.kod))
                    if (s == null) onZatvori() else uredujem = false
                }
            }, ikona = "check")
        } else if (s != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalniGumb("Preimenuj", { uredujem = true }, Modifier.weight(1f), boja = Neon.Cijan)
                TonalniGumb("Obriši", { potvrda = true }, Modifier.weight(1f), boja = Neon.Crvena)
            }
        }
    }
    if (biramRok && s != null) {
        BiracDatumaDijalog(s.novcanik.rokEpochDan?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusYears(1), {
            onSpremi(s.novcanik.copy(rokEpochDan = it.toEpochDay()))
        }) { biramRok = false }
    }
    if (potvrda && s != null) {
        PotvrdaBrisanja("Obrisati novčanik?", "\"${s.novcanik.naziv}\" i sva njegova povijest bit će obrisani.", { potvrda = false }) {
            onObrisi(s.novcanik); potvrda = false; onZatvori()
        }
    }
    if (s != null) uredujemPromjenu?.let { p ->
        UrediPromjenuNovcanikaDijalog(
            p, s.valuta, onSpremi = { onUrediPromjenu(it); uredujemPromjenu = null },
            onObrisi = { brisemPromjenu = p; uredujemPromjenu = null },
        ) { uredujemPromjenu = null }
    }
    brisemPromjenu?.let { p ->
        PotvrdaBrisanja("Obrisati promjenu?", "${HrDatum.puni(LocalDate.ofEpochDay(p.epochDan))} · ${Valute.iznos(p.jedinice, s!!.valuta)}", { brisemPromjenu = null }) {
            onObrisiPromjenu(p); brisemPromjenu = null
        }
    }
}

/**
 * Kalkulator valuta: preračun bilo kojeg iznosa i ukupna štednja (novčanici i ciljevi) u odabranoj valuti.
 * Tečajevi se dohvaćaju online pri svakom otvaranju.
 */
@Composable
fun KalkulatorValutaSheet(
    novcanici: List<StanjeNovcanika>,
    ciljevi: List<CiljStednje>,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
    uplateCiljeva: List<hr.smartbudget.app.data.UplataCilja>,
    onZatvori: () -> Unit,
) {
    LaunchedEffect(Unit) { onOsvjezi() }
    val osnovna = osnovnaValuta()
    var iz by remember { mutableStateOf(if (osnovna.kod == "EUR") Valute.po("USD") else osnovna) }
    var u by remember { mutableStateOf(if (osnovna.kod == "EUR") osnovna else Valute.po("EUR")) }
    var iznos by remember { mutableStateOf("100") }
    var ukupnoU by remember { mutableStateOf(osnovna) }

    NeonSheet(onZatvori) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kalkulator valuta", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            KruzniGumb("osvjezi", "Osvježi tečajeve", onOsvjezi, boja = Neon.Cijan)
            Spacer(Modifier.width(8.dp))
            KruzniGumb("close", "Zatvori", onZatvori)
        }
        Text(
            "Valute se preračunavaju po srednjem tečaju Hrvatske narodne banke (HNB), a kriptovalute po trenutnoj cijeni s CoinGecka. " +
                "Podaci se dohvaćaju online pri svakom otvaranju.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
        )
        IzvorTecajeva(t, kripto = true)

        // Preračun
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.Ispuna)
                .border(1.dp, Neon.RubSlab, RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeonPolje(
                iznos, { iznos = it }, Modifier.fillMaxWidth(), placeholder = "Iznos", tipkovnica = KeyboardType.Decimal,
                stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, fontFeatureSettings = "tnum"),
            )
            GumbValute(iz, Modifier.fillMaxWidth()) { iz = it }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                KruzniGumb("zamijeni", "Zamijeni valute", { val x = iz; iz = u; u = x }, velicina = 40.dp, boja = Neon.Cijan)
            }
            GumbValute(u, Modifier.fillMaxWidth()) { u = it }
            val broj = Valute.parsiraj(iznos, Valute.po("BTC"))?.let { Valute.uBroj(it, Valute.po("BTC")) }
            val rez = broj?.let { hr.smartbudget.app.data.Tecajevi.pretvori(it, iz, u, t.hnb, t.kripto) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        broj == null -> "Unesite iznos"
                        rez == null -> if (t.ucitava) "…" else "Nema tečaja"
                        else -> Valute.iznos(rez, u)
                    },
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena, maxLines = 1,
                )
                hr.smartbudget.app.data.Tecajevi.pretvori(1.0, iz, u, t.hnb, t.kripto)?.let {
                    Text("1 ${iz.kod} = ${Valute.iznos(it, u)}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                }
            }
        }

        // Ukupna štednja u jednoj valuti
        val ciljeviUkupno = ciljevi.sumOf { it.ustedjenoCenti }
        // Uplate na ciljeve u drugim valutama, zbrojene po valuti
        val ciljeviValute = ciljevi.flatMap { hr.smartbudget.app.domain.valuteCilja(it.id, uplateCiljeva).entries }
            .groupBy { it.key }.mapValues { (_, l) -> l.sumOf { it.value } }
        if (novcanici.isNotEmpty() || ciljeviUkupno > 0 || ciljeviValute.isNotEmpty()) {
            Text("Ukupna štednja u jednoj valuti", style = Tip.naslovKartice)
            GumbValute(ukupnoU, Modifier.fillMaxWidth()) { ukupnoU = it }
            var zbroj = 0.0
            var potpuno = true
            Column {
                val stavke = novcanici.map { Triple(it.novcanik.naziv, it.stanje, it.valuta) } +
                    (if (ciljeviUkupno > 0) listOf(Triple("Ciljevi štednje", ciljeviUkupno, osnovna)) else emptyList()) +
                    ciljeviValute.map { (k, j) -> Triple("Ciljevi štednje (${k})", j, Valute.po(k)) }
                stavke.forEachIndexed { i, (n, j, v) ->
                    if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                    val p = hr.smartbudget.app.data.Tecajevi.pretvori(Valute.uBroj(j, v), v, ukupnoU, t.hnb, t.kripto)
                    if (p != null) zbroj += p else potpuno = false
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Valute.iznos(j, v), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
                        }
                        Text(p?.let { Valute.iznos(it, ukupnoU) } ?: "—", style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Neon.Zelena.copy(alpha = .1f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ukupno", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text((if (potpuno) "" else "≥ ") + Valute.iznos(zbroj, ukupnoU), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Tab "Štednja" na Početnoj: ukupno ušteđeno (ciljevi + novčanici, preračunato u osnovnu valutu),
 * brzi prijenos s računa, ciljevi štednje i štednja u valutama.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StednjaTab(
    ciljevi: List<CiljStednje>,
    uplate: List<hr.smartbudget.app.data.UplataCilja>,
    novcanici: List<StanjeNovcanika>,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
    onPrijenos: () -> Unit,
    ciljeviKartica: @Composable () -> Unit,
    novcaniciKartica: @Composable () -> Unit,
    /** Vrijednost ulaganja (dionice, ETF-ovi, obveznice, roba) u osnovnoj valuti i njihov broj. */
    ulaganja: Double = 0.0,
    brojUlaganja: Int = 0,
    ulaganjaKartica: @Composable () -> Unit = {},
    /** Za dijeljenje i izvoz cijele štednje. */
    pozicije: List<hr.smartbudget.app.domain.StanjePozicije> = emptyList(),
    cijene: hr.smartbudget.app.ui.CijeneUlaganja = hr.smartbudget.app.ui.CijeneUlaganja(),
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val opseg = androidx.compose.runtime.rememberCoroutineScope()
    // Dodir na pločicu (Ciljevi, U valutama, Ulaganja) pomiče na tu karticu, s vrhom kartice pri vrhu zaslona.
    val doCiljeva = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val doValuta = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val doUlaganja = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    fun skoci(r: androidx.compose.foundation.relocation.BringIntoViewRequester) = opseg.launch {
        r.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 100_000f))
    }
    // Izvoz štednje u Excel ili CSV
    var biramIzvoz by remember { mutableStateOf(false) }
    fun izvezi(format: hr.smartbudget.app.export.FormatIzvoza, uri: android.net.Uri) = opseg.launch {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)!!.use { izveziStednju(format, it, ciljevi, uplate, novcanici, pozicije, cijene, t) }
            }
        }.onSuccess { n -> android.widget.Toast.makeText(ctx, "Izvezeno stavki štednje: $n", android.widget.Toast.LENGTH_SHORT).show() }
            .onFailure { e -> android.widget.Toast.makeText(ctx, "Izvoz nije uspio: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
    }
    val nazivIzvoza = "SmartBudget_stednja_${java.time.LocalDate.now()}"
    val izvozXlsx = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(hr.smartbudget.app.export.FormatIzvoza.XLSX.mime),
    ) { uri -> if (uri != null) izvezi(hr.smartbudget.app.export.FormatIzvoza.XLSX, uri) }
    val izvozCsv = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(hr.smartbudget.app.export.FormatIzvoza.CSV.mime),
    ) { uri -> if (uri != null) izvezi(hr.smartbudget.app.export.FormatIzvoza.CSV, uri) }
    fun podijeliDatoteku() = opseg.launch {
        runCatching {
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = java.io.File(ctx.cacheDir, "izvoz").apply { mkdirs() }
                java.io.File(dir, "$nazivIzvoza.xlsx").also { f -> f.outputStream().use { izveziStednju(hr.smartbudget.app.export.FormatIzvoza.XLSX, it, ciljevi, uplate, novcanici, pozicije, cijene, t) } }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = hr.smartbudget.app.export.FormatIzvoza.XLSX.mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "SmartBudget štednja")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(android.content.Intent.createChooser(send, "Podijeli izvoz štednje").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { e -> android.widget.Toast.makeText(ctx, "Dijeljenje nije uspjelo: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
    }
    if (biramIzvoz) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { biramIzvoz = false },
            containerColor = Neon.Sheet,
            title = { Text("Izvoz štednje", color = Neon.Tekst) },
            text = {
                Text(
                    "Ciljevi, novčanici i ulaganja s vrijednošću u ${osnovnaValuta().kod}, uloženim iznosom, dobiti i rokom.",
                    color = Neon.TekstSvijetliSiv,
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    androidx.compose.material3.TextButton({ biramIzvoz = false; izvozXlsx.launch("$nazivIzvoza.xlsx") }) { Text("Spremi Excel (.xlsx)", color = Neon.Zelena) }
                    androidx.compose.material3.TextButton({ biramIzvoz = false; izvozCsv.launch("$nazivIzvoza.csv") }) { Text("Spremi CSV", color = Neon.Zelena) }
                    androidx.compose.material3.TextButton({ biramIzvoz = false; podijeliDatoteku() }) { Text("Podijeli Excel", color = Neon.Cijan) }
                    androidx.compose.material3.TextButton({ biramIzvoz = false }) { Text("Odustani", color = Neon.TekstSiv) }
                }
            },
        )
    }
    val treba = novcanici.isNotEmpty() || uplate.any { it.valuta != null }
    LaunchedEffect(treba) { if (treba) onOsvjezi() }
    val osnovna = osnovnaValuta()
    val uOsnovnu: (Long, Valuta) -> Double? = { j, v -> uOsnovnuValutu(j, v, t) }
    val uCiljevima = ciljevi.sumOf { hr.smartbudget.app.domain.ustedjenoCilja(it, uplate, uOsnovnu).centi } / 100.0
    val uNovcanicima = novcanici.sumOf { uOsnovnu(it.stanje, it.valuta) ?: 0.0 }
    val ukupnoCilj = ciljevi.sumOf { it.ciljCenti } / 100.0
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        hr.smartbudget.app.ui.Kartica(razmak = 12.dp, sjaj = Neon.LjubicastaSvijetla) {
            Text("Ukupna štednja", style = Tip.oznaka)
            Text(Valute.iznos(uCiljevima + uNovcanicima + ulaganja, osnovna), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Neon.LjubicastaTekst, maxLines = 1, modifier = Modifier.skrivaStednju())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlocicaStednje("Ciljevi", Valute.iznos(uCiljevima, osnovna), if (ukupnoCilj > 0) "od ${Valute.iznos(ukupnoCilj, osnovna)}" else "${ciljevi.size} ciljeva", Modifier.weight(1f)) { skoci(doCiljeva) }
                PlocicaStednje("U valutama", Valute.iznos(uNovcanicima, osnovna), "${novcanici.size} novčanika", Modifier.weight(1f)) { skoci(doValuta) }
            }
            if (brojUlaganja > 0) {
                PlocicaStednje("Ulaganja", Valute.iznos(ulaganja, osnovna), "$brojUlaganja · dionice, ETF-ovi, obveznice, roba", Modifier.fillMaxWidth()) { skoci(doUlaganja) }
            }
            TonalniGumb("Prenesi s računa na štednju", onPrijenos, Modifier.fillMaxWidth(), ikona = "transfer", boja = Neon.LjubicastaSvijetla, visina = 48.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalniGumb("Podijeli", {
                    podijeliTekst(ctx, "SmartBudget štednja", tekstSveStednje(ciljevi, uplate, novcanici, pozicije, cijene, t))
                }, Modifier.weight(1f), ikona = "share", boja = Neon.Cijan, visina = 44.dp)
                TonalniGumb("Izvezi", { biramIzvoz = true }, Modifier.weight(1f), ikona = "download", boja = Neon.Zelena, visina = 44.dp)
            }
            if (novcanici.any { it.valuta.kod != osnovna.kod } || uplate.any { it.valuta != null }) {
                IzvorTecajeva(t, kripto = novcanici.any { it.valuta.kripto }, fiat = true)
            }
        }
        Box(Modifier.bringIntoViewRequester(doCiljeva)) { ciljeviKartica() }
        Box(Modifier.bringIntoViewRequester(doValuta)) { novcaniciKartica() }
        Box(Modifier.bringIntoViewRequester(doUlaganja)) { ulaganjaKartica() }
    }
}

@Composable
private fun PlocicaStednje(naziv: String, iznos: String, opis: String, modifier: Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Neon.Ispuna).border(1.dp, Neon.RubSlab, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Prikaži: $naziv", onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(naziv, style = Tip.oznaka)
        Text(iznos, style = Tip.iznos.copy(fontSize = 16.sp), color = Neon.Tekst, maxLines = 1)
        Text(opis, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp), maxLines = 1)
    }
}


/** Uređivanje uplate ili isplate u novčaniku: iznos u valuti novčanika, plaćeno (za dobit), datum i napomena. */
@Composable
private fun UrediPromjenuNovcanikaDijalog(
    p: PromjenaNovcanika, v: Valuta,
    onSpremi: (PromjenaNovcanika) -> Unit, onObrisi: () -> Unit, onZatvori: () -> Unit,
) {
    val osnovna = osnovnaValuta()
    val uplata = p.jedinice >= 0
    var iznos by remember { mutableStateOf(Valute.zaUnos(kotlin.math.abs(p.jedinice), v)) }
    var placeno by remember { mutableStateOf(p.ulozenoCenti?.let { Novac.broj(it) } ?: "") }
    var napomena by remember { mutableStateOf(p.napomena) }
    var datum by remember { mutableStateOf(LocalDate.ofEpochDay(p.epochDan)) }
    var biram by remember { mutableStateOf(false) }
    var greska by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        title = { Text(if (uplata) "Uredi uplatu" else "Uredi isplatu", color = Neon.Tekst) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OznakaPolja("Iznos (${v.kod})")
                NeonPolje(iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(), placeholder = if (v.kripto) "0,00000000" else "0,00", tipkovnica = KeyboardType.Decimal)
                if (uplata && v.kod != osnovna.kod) {
                    OznakaPolja("Plaćeno (${osnovna.kod}, za izračun dobiti)")
                    NeonPolje(placeno, { placeno = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                }
                GumbPolja("calendar", "Datum ${HrDatum.puni(datum)}", Modifier.fillMaxWidth()) { biram = true }
                NeonPolje(napomena, { napomena = it }, Modifier.fillMaxWidth(), placeholder = "Napomena (opcionalno)")
                greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                val j = Valute.parsiraj(iznos, v)
                if (j == null || j <= 0) greska = "Unesite iznos."
                else onSpremi(
                    p.copy(
                        jedinice = if (uplata) j else -j, epochDan = datum.toEpochDay(), napomena = napomena.trim(),
                        ulozenoCenti = if (uplata && v.kod != osnovna.kod) Novac.parsiraj(placeno)?.takeIf { it > 0 } else p.ulozenoCenti,
                    ),
                )
            }) { Text("Spremi", color = Neon.Zelena, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(onObrisi) { Text("Obriši", color = Neon.Crvena) }
                androidx.compose.material3.TextButton(onZatvori) { Text("Odustani", color = Neon.TekstSiv) }
            }
        },
    )
    if (biram) BiracDatumaDijalog(datum, { datum = it }) { biram = false }
}
