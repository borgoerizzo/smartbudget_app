package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.ui.StanjeRacunaUi
import hr.smartbudget.app.data.StanjePostavki
import hr.smartbudget.app.data.TipTransakcije
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.domain.vrijeme
import hr.smartbudget.app.ui.FilterTransakcija
import hr.smartbudget.app.ui.GrupaDana
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.NeonChip
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PillKategorije
import hr.smartbudget.app.ui.PocetnaStanje
import hr.smartbudget.app.ui.PraznoStanje
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.BiracCiklusa
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PocetnaEkran(
    s: PocetnaStanje,
    onPrethodni: () -> Unit,
    onSljedeci: () -> Unit,
    onTrenutni: () -> Unit,
    onUrediCiklus: () -> Unit,
    onFilter: (FilterTransakcija) -> Unit,
    onTransakcija: (TransakcijaSKategorijom) -> Unit,
    onDodaj: () -> Unit,
    onSpremiRaspon: (LocalDate, LocalDate) -> Unit,
    onVratiZadaniRaspon: () -> Unit,
    onPregled: (TipTransakcije) -> Unit,
    onPretraga: (String) -> Unit,
    onPretragaSvi: (Boolean) -> Unit,
    racuni: List<StanjeRacunaUi>,
    onRacun: (Racun) -> Unit,
    onNoviRacun: () -> Unit,
    onPrijenos: () -> Unit,
) {
    val privatnost by AppPostavke.get(LocalContext.current).stanje.collectAsState()
    var biramRaspon by remember { mutableStateOf(false) }
    if (biramRaspon) {
        RasponCiklusaDijalog(
            s, onZatvori = { biramRaspon = false },
            onSpremi = { od, doDana -> biramRaspon = false; onSpremiRaspon(od, doDana) },
            onVratiZadano = { biramRaspon = false; onVratiZadaniRaspon() },
        )
    }
    val stanjeListe = rememberLazyListState()
    var fokusPretrage by remember { mutableStateOf(false) }
    // Visina tipkovnice: popis dobiva toliki donji razmak, pa ništa ne ostaje skriveno ispod nje.
    val tipkovnicaDno = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val pomakRezultata = with(LocalDensity.current) { (VISINA_PRETRAGE + 8.dp).roundToPx() }
    // Dok je pretraga u fokusu: polje zalijepljeno na vrhu, prvi rezultat (stavka 4) odmah ispod njega,
    // ponovno nakon svakog znaka i kad se tipkovnica pojavi.
    LaunchedEffect(fokusPretrage, tipkovnicaDno) {
        if (fokusPretrage) stanjeListe.animateScrollToItem(5, -pomakRezultata)
    }
    // Kod tipkanja bez animacije i samo ako je popis pomaknut, da brzi unos ne izgubi znak.
    LaunchedEffect(s.pretraga, s.grupe.size) {
        if (fokusPretrage && stanjeListe.firstVisibleItemIndex != 4 && stanjeListe.firstVisibleItemIndex != 5) {
            stanjeListe.scrollToItem(5, -pomakRezultata)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            state = stanjeListe,
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 20.dp,
                bottom = maxOf(132.dp, tipkovnicaDno + 16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Zaglavlje(s, onPrethodni, onSljedeci, onTrenutni, onUrediCiklus) { biramRaspon = true } }
            item { HeroKartica(s, privatnost, onPregled) }
            item { RacuniTraka(racuni, privatnost.sakrijStanje, onRacun, onNoviRacun, onPrijenos) }
            stickyHeader {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    PoljePretrage(s.pretraga, onPretraga, s.pretragaSvi, onPretragaSvi) { fokusPretrage = it }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Transakcije", style = Tip.naslov)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterTransakcija.entries.forEach { f -> NeonChip(f.naziv, s.filter == f, { onFilter(f) }) }
                    }
                }
            }
            if (s.ucitano && s.grupe.isEmpty()) {
                item {
                    PraznoStanje(
                        naslov = when {
                            s.pretraga.isNotBlank() -> "Nema rezultata za \"${s.pretraga.trim()}\""
                            s.filter == FilterTransakcija.SVE -> "Još nema transakcija"
                            else -> "Nema transakcija za ovaj filter"
                        },
                        tekst = if (s.pretraga.isNotBlank()) "Pretraga obuhvaća naziv, kategoriju, način plaćanja i iznos u ovom ciklusu."
                        else "Dodajte prvi prihod ili trošak u ovom ciklusu gumbom +.",
                        akcija = if (s.pretraga.isNotBlank()) null else "Dodaj transakciju",
                        onAkcija = onDodaj,
                    )
                }
            }
            items(s.grupe, key = { it.datum.toEpochDay() }) { g -> GrupaDanaKartica(g, privatnost.sakrijPrihod, onTransakcija) }
            // Prostor ispod rezultata, da se i kratki popis može poravnati odmah ispod polja pretrage.
            if (fokusPretrage) item { Spacer(Modifier.fillParentMaxHeight()) }
        }
        // Brzi unos (FAB 60 dp, zaobljenje 20 dp)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 106.dp)
                .size(60.dp)
                .neonSjaj(Neon.Zelena, 24.dp, 20.dp, .45f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Neon.Zelena, Neon.Cijan)))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(20.dp))
                .clickable(onClickLabel = "Brzo dodaj transakciju", onClick = onDodaj),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ikone.ui("plus"), "Brzo dodaj transakciju", Modifier.size(28.dp), tint = Neon.NaGumbu)
        }
    }
}

/** Pretraga transakcija ciklusa; popis ispod se filtrira odmah dok se tipka. */
private val VISINA_PRETRAGE = 52.dp + 12.dp

@Composable
private fun PoljePretrage(upit: String, onPretraga: (String) -> Unit, svi: Boolean, onSvi: (Boolean) -> Unit, onFokus: (Boolean) -> Unit) {
    val tipkovnica = LocalSoftwareKeyboardController.current
    var tekst by rememberSaveable { mutableStateOf(upit) }
    val oblik = RoundedCornerShape(24.dp)
    var fokus by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(oblik)
            .background(Color(0xFF161D27)) // neprozirno: popis klizi ispod zalijepljenog polja
            .border(1.dp, if (fokus) Neon.Cijan.copy(alpha = .6f) else Neon.Rub, oblik)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ikone.ui("search"), null, Modifier.size(20.dp), tint = if (fokus) Neon.Cijan else Neon.TekstSiv)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (tekst.isEmpty()) Text("Pretraži transakcije…", style = Tip.tijelo.copy(color = Neon.Neaktivno, fontWeight = FontWeight.Normal))
            BasicTextField(
                value = tekst,
                onValueChange = { tekst = it; onPretraga(it) },
                singleLine = true,
                textStyle = Tip.tijelo,
                cursorBrush = SolidColor(Neon.Cijan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { tipkovnica?.hide() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (it.isFocused != fokus) onFokus(it.isFocused)
                    fokus = it.isFocused
                },
            )
        }
        // Pretraga kroz sve cikluse ili samo kroz prikazani
        if (tekst.isNotEmpty() || svi) {
            Text(
                if (svi) "Svi ciklusi" else "Ovaj ciklus",
                color = if (svi) Neon.Cijan else Neon.TekstSiv, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (svi) Neon.Cijan.copy(alpha = .14f) else Color(0x0FFFFFFF))
                    .clickable(onClickLabel = "Promijeni opseg pretrage") { onSvi(!svi) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        if (tekst.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Obriši pretragu") { tekst = ""; onPretraga("") },
                contentAlignment = Alignment.Center,
            ) { Icon(Ikone.ui("close"), "Obriši pretragu", Modifier.size(18.dp), tint = Neon.TekstSiv) }
        }
    }
}

@Composable
private fun Zaglavlje(
    s: PocetnaStanje,
    onPrethodni: () -> Unit,
    onSljedeci: () -> Unit,
    onTrenutni: () -> Unit,
    onUredi: () -> Unit,
    onRaspon: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (s.jeAktivni) "AKTIVNI CIKLUS" else "OBRAČUNSKI CIKLUS") + if (s.prilagoden) " · RUČNI RASPON" else "",
                    color = if (s.prilagoden) Neon.LjubicastaTekst else Neon.TekstSiv,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!s.jeAktivni) {
                    Text(
                        "Danas", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onTrenutni).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            // Dodir na raspon otvara ručni odabir početka i kraja ovog ciklusa.
            BiracCiklusa(HrDatum.raspon(s.ciklus), onPrethodni, onSljedeci, onRaspon)
        }
        KruzniGumb("edit", "Uredi dan početka ciklusa", onUredi, velicina = 48.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RasponCiklusaDijalog(
    s: PocetnaStanje,
    onZatvori: () -> Unit,
    onSpremi: (LocalDate, LocalDate) -> Unit,
    onVratiZadano: () -> Unit,
) {
    fun ms(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun dan(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    val st = rememberDateRangePickerState(
        initialSelectedStartDateMillis = ms(s.ciklus.pocetak),
        initialSelectedEndDateMillis = ms(s.ciklus.kraj),
        initialDisplayedMonthMillis = ms(s.ciklus.pocetak),
    )
    DatePickerDialog(
        onDismissRequest = onZatvori,
        confirmButton = {
            TextButton(
                {
                    val od = st.selectedStartDateMillis
                    val doDana = st.selectedEndDateMillis
                    if (od != null && doDana != null) onSpremi(dan(od), dan(doDana))
                },
                enabled = st.selectedStartDateMillis != null && st.selectedEndDateMillis != null,
            ) { Text("Spremi", color = Neon.Zelena, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (s.prilagoden) TextButton(onVratiZadano) { Text("Vrati zadano", color = Neon.LjubicastaTekst) }
                TextButton(onZatvori) { Text("Odustani", color = Neon.TekstSiv) }
            }
        },
        colors = DatePickerDefaults.colors(containerColor = Neon.Sheet),
    ) {
        DateRangePicker(
            st,
            modifier = Modifier.weight(1f),
            title = {
                Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) {
                    Text("Raspon ovog ciklusa", style = Tip.naslovKartice)
                    Text(
                        "Odaberite početak i kraj. Susjedni ciklusi se prilagode sami, bez preklapanja.",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                    )
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Neon.Sheet,
                selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu,
                dayInSelectionRangeContainerColor = Neon.Zelena.copy(alpha = .18f), dayInSelectionRangeContentColor = Neon.Tekst,
                todayDateBorderColor = Neon.Cijan, todayContentColor = Neon.Cijan,
            ),
        )
    }
}

@Composable
private fun HeroKartica(s: PocetnaStanje, privatnost: StanjePostavki, onPregled: (TipTransakcije) -> Unit) {
    val neto = s.neto
    val netoBoja = if (neto >= 0) Neon.Zelena else Neon.Crvena
    Kartica(
        pozadina = Neon.KarticaSvijetla,
        rub = Neon.Rub,
        sjaj = netoBoja,
        padding = 20.dp,
        razmak = 18.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Neto stanje ciklusa", color = Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Oznaka(
                when {
                    s.jeAktivni -> "Dan ${s.protekloDana} od ${s.ciklus.brojDana}"
                    s.protekloDana == 0 -> "Budući ciklus"
                    else -> "Završen · ${s.ciklus.brojDana} dana"
                },
                Neon.Cijan,
            )
        }
        // Skriveno stanje: dodir na iznos ga privremeno otkrije.
        var otkriveno by remember { mutableStateOf(false) }
        val sakrijStanje = privatnost.sakrijStanje && !otkriveno
        val sakrijPrihod = privatnost.sakrijPrihod && !otkriveno
        val iznos = if (sakrijStanje) "••••" else Novac.stanje(neto).removeSuffix(" €")
        Text(
            modifier = Modifier.clickable(
                enabled = privatnost.sakrijStanje || privatnost.sakrijPrihod,
                onClickLabel = if (otkriveno) "Sakrij iznose" else "Prikaži iznose",
            ) { otkriveno = !otkriveno },
            text = buildAnnotatedString {
                append(iznos)
                withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)) { append(" €") }
            },
            style = TextStyle(
                fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = netoBoja, letterSpacing = (-0.9).sp,
                fontFeatureSettings = "tnum", shadow = Shadow(netoBoja.copy(alpha = .45f), blurRadius = 28f),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubMetrika("Prihodi", if (sakrijPrihod) AppPostavke.SKRIVENO else Novac.predznak(s.zbroj.prihodi), Neon.Zelena, "arrowDownLeft", Modifier.weight(1f)) { onPregled(TipTransakcije.PRIHOD) }
            SubMetrika("Rashodi", Novac.predznak(-s.zbroj.troskovi), Neon.Crvena, "arrowUpRight", Modifier.weight(1f)) { onPregled(TipTransakcije.TROSAK) }
        }
        if (s.prijenos < 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Neon.Ispuna)
                    .border(1.dp, Neon.RubSlab, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Prijenos iz prošlog ciklusa", style = Tip.tijelo.copy(fontSize = 13.sp))
                    Text("Minus s kraja prošlog ciklusa umanjuje nove prihode", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                }
                Text(Novac.predznak(s.prijenos), color = Neon.Crvena, style = Tip.iznos)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Potrošnja i preostali dani", style = Tip.tijelo.copy(fontSize = 13.sp))
                if (s.jeAktivni) {
                    Text(
                        if (s.preostaloDana == 1) "još 1 dan" else "još ${s.preostaloDana} dana",
                        color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TrakaNapretka(
                udio = (s.postoPotroseno / 100).toFloat(),
                ispuna = Brush.horizontalGradient(listOf(Neon.Cijan, Neon.Crvena)),
                sjaj = Neon.Crvena,
                visina = 14.dp,
                marker = (s.postoCiklusa / 100).toFloat(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Potrošeno ${Novac.postotak(s.postoPotroseno)} prihoda", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                Text("Prošlo ${Novac.postotak(s.postoCiklusa)} ciklusa", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MalaMetrika("Stanje računa", if (sakrijStanje) AppPostavke.SKRIVENO else Novac.stanje(s.ukupnoStanje), if (s.ukupnoStanje >= 0) Neon.Tekst else Neon.Crvena, Modifier.weight(1f))
            MalaMetrika("Dnevni prosjek", Novac.eur(s.dnevniProsjek) + "/dan", Neon.Tekst, Modifier.weight(1f))
        }
        s.tempo?.let { (tekstSIznosom, upozorenje) ->
            val tekst = if (sakrijStanje) s.tempoBezIznosa ?: tekstSIznosom else tekstSIznosom
            val boja = if (upozorenje) Neon.Crvena else Neon.Zelena
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(boja.copy(alpha = .11f))
                    .border(1.dp, boja.copy(alpha = .28f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ikone.ui(if (upozorenje) "warn" else "check"), null, Modifier.size(18.dp), tint = boja)
                Text(tekst, fontSize = 13.sp, lineHeight = 18.sp, color = if (upozorenje) Color(0xFFFFC2CE) else Color(0xFFB8FFE3))
            }
        }
    }
}

@Composable
private fun SubMetrika(naslov: String, iznos: String, boja: Color, ikona: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Neon.Ispuna)
            .border(1.dp, Neon.RubSlab, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Pregled: $naslov", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(boja.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Ikone.ui(ikona), null, Modifier.size(16.dp), tint = boja)
            }
            Text(naslov, style = Tip.oznaka, modifier = Modifier.weight(1f))
            Icon(Ikone.ui("chevRight"), null, Modifier.size(14.dp), tint = Neon.TekstSiv)
        }
        Text(iznos, color = boja, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = Tip.iznos, maxLines = 1)
    }
}

@Composable
private fun MalaMetrika(naslov: String, vrijednost: String, boja: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(naslov, style = Tip.oznaka)
        Text(vrijednost, color = boja, style = Tip.iznos, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun GrupaDanaKartica(g: GrupaDana, sakrijPrihod: Boolean, onTransakcija: (TransakcijaSKategorijom) -> Unit) {
    // Dnevni zbroj otkriva prihod ako ga dan sadrži.
    val sakrijZbroj = sakrijPrihod && g.stavke.any { it.transakcija.tip == TipTransakcije.PRIHOD }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(g.naslov, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(8.dp))
            Text(g.podnaslov, style = Tip.malo, modifier = Modifier.weight(1f))
            Text(if (sakrijZbroj) AppPostavke.SKRIVENO else Novac.predznak(g.neto), color = if (g.neto >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tip.iznos)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Neon.Kartica)
                .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(20.dp)),
        ) {
            g.stavke.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = Neon.RubSlab, thickness = 1.dp)
                StavkaTransakcije(t, sakrijPrihod = sakrijPrihod) { onTransakcija(t) }
            }
        }
    }
}

@Composable
fun StavkaTransakcije(
    t: TransakcijaSKategorijom,
    udio: String? = null,
    sDatumom: Boolean = false,
    sakrijPrihod: Boolean = false,
    onClick: () -> Unit,
) {
    val prihod = t.transakcija.tip == TipTransakcije.PRIHOD
    val prijenos = t.transakcija.jePrijenos
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Uredi transakciju", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                t.transakcija.opis.ifBlank { t.kategorija.naziv },
                style = Tip.tijelo, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (prihod && sakrijPrihod) "+" + AppPostavke.SKRIVENO
                    else Novac.predznak(if (prihod) t.transakcija.iznosCenti else -t.transakcija.iznosCenti),
                    style = Tip.iznos, color = when { prijenos -> Neon.TekstSvijetliSiv; prihod -> Neon.Zelena; else -> Neon.Crvena },
                )
                // Udio u ukupnom (prikaz u pregledu prihoda/rashoda)
                if (udio != null) Text(udio, style = Tip.oznaka.copy(fontSize = 11.sp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                if (prijenos) {
                    Row(
                        Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(Neon.Cijan.copy(alpha = .14f)).padding(start = 7.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ikone.ui("transfer"), null, Modifier.size(14.dp), tint = Neon.Cijan)
                        Text(" Prijenos", color = Neon.Cijan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    PillKategorije(t.kategorija)
                }
            }
            if (t.transakcija.slika != null) {
                Icon(Ikone.ui("image"), "Ima fotografiju računa", Modifier.size(14.dp), tint = Neon.TekstSiv)
            }
            if (t.transakcija.ponavljajuciId != null) {
                Icon(Ikone.ikona("repeat"), "Ponavljajući unos", Modifier.size(14.dp), tint = Neon.LjubicastaTekst)
            }
            val kada = (if (sDatumom) HrDatum.kratko(t.datum()) + " " else "") + HrDatum.vrijeme(t.vrijeme())
            Text("${t.transakcija.nacinPlacanja} · $kada", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
        }
    }
}
