package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.NacinPlacanja
import hr.smartbudget.app.data.Fotografije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.Pretraga
import hr.smartbudget.app.domain.prijedloziZaNaziv
import hr.smartbudget.app.ui.TonalniGumb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hr.smartbudget.app.data.PonavljajuciUnos
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.Ponavljanje
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.NeonChip
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import hr.smartbudget.app.ui.theme.svjetlija
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonSheet(onZatvori: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onZatvori,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Neon.Sheet,
        contentColor = Neon.Tekst,
        scrimColor = Color(0xAD05080C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF)))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun ZaglavljeSheeta(naslov: String, onZatvori: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(naslov, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        KruzniGumb("close", "Zatvori", onZatvori)
    }
}

@Composable
fun OznakaPolja(tekst: String) {
    Text(tekst, style = Tip.oznaka)
}

@Composable
fun NeonPolje(
    vrijednost: String,
    onPromjena: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    tipkovnica: KeyboardType = KeyboardType.Text,
    stil: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neon.Tekst),
    visina: Dp = 52.dp,
    greska: Boolean = false,
    fokusBoja: Color = Neon.Ljubicasta,
    /**
     * Unos iznosa "od centa prema eurima": svaka znamenka ulazi s desna (1 → 0,01, 10 → 0,10, 1000 → 10,00),
     * brisanje miče zadnju znamenku. Vrijednost je uvijek formatirana (1.250,00), kursor stoji na kraju.
     */
    uCentima: Boolean = false,
) {
    var fokus by remember { mutableStateOf(false) }
    val rub = when {
        greska -> Neon.Crvena
        fokus -> fokusBoja.copy(alpha = .7f)
        else -> Color(0x1FFFFFFF)
    }
    val tipkovnicaOpcije = KeyboardOptions(
        keyboardType = if (uCentima) KeyboardType.Number else tipkovnica,
        capitalization = if (tipkovnica == KeyboardType.Text && !uCentima) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
    )
    val mod = modifier.height(visina).onFocusChanged { fokus = it.isFocused }
    val ukras: @Composable (@Composable () -> Unit) -> Unit = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(visina)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, rub, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (vrijednost.isEmpty()) Text(placeholder, style = stil.copy(color = Neon.Neaktivno, fontWeight = FontWeight.Normal))
                inner()
            }
    }
    if (uCentima) {
        BasicTextField(
            value = TextFieldValue(vrijednost, TextRange(vrijednost.length)),
            onValueChange = { novo ->
                val znamenke = novo.text.filter { it.isDigit() }.trimStart('0').take(11)
                onPromjena(if (znamenke.isEmpty()) "" else Novac.broj(znamenke.toLong()))
            },
            singleLine = true, textStyle = stil, cursorBrush = SolidColor(fokusBoja),
            keyboardOptions = tipkovnicaOpcije, modifier = mod, decorationBox = ukras,
        )
    } else {
        BasicTextField(
            value = vrijednost, onValueChange = onPromjena,
            singleLine = true, textStyle = stil, cursorBrush = SolidColor(fokusBoja),
            keyboardOptions = tipkovnicaOpcije, modifier = mod, decorationBox = ukras,
        )
    }
}

/** Segmentirana kontrola (Trošak/Prihod, format izvoza...). */
@Composable
fun Segmenti(opcije: List<String>, odabrano: Int, onOdabir: (Int) -> Unit, modifier: Modifier = Modifier, boje: List<Color>? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xBF1A222D))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(26.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opcije.forEachIndexed { i, o ->
            val akt = i == odabrano
            val boja = boje?.getOrNull(i) ?: Neon.Zelena
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .then(if (akt) Modifier.neonSjaj(boja, 14.dp, 22.dp, .22f) else Modifier)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (akt) boja.copy(alpha = .14f) else Color.Transparent)
                    .border(1.dp, if (akt) boja.copy(alpha = .45f) else Color.Transparent, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Tab) { onOdabir(i) },
                contentAlignment = Alignment.Center,
            ) {
                // Dug tekst se centrira i lomi u najviše dva reda umjesto da se reže (veliki font sustava).
                Text(
                    o, color = if (akt) boja else Neon.TekstSrednji, fontSize = 14.sp, lineHeight = 16.sp,
                    fontWeight = if (akt) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChipKategorije(k: Kategorija, odabran: Boolean, onClick: () -> Unit) {
    val boja = hexBoja(k.bojaHex)
    val oblik = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(oblik)
            .background(if (odabran) boja.copy(alpha = .18f) else Color(0x08FFFFFF))
            .border(1.dp, if (odabran) boja.copy(alpha = .7f) else Color(0x1AFFFFFF), oblik)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Ikone.ikona(k.ikonaNaziv), null, Modifier.size(16.dp), tint = if (odabran) boja.svjetlija() else Neon.TekstSiv)
        Text(k.naziv, color = if (odabran) Neon.Tekst else Neon.TekstSvijetliSiv, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransakcijaSheet(
    postojeca: Transakcija?,
    kategorije: List<Kategorija>,
    onSpremi: (Transakcija) -> Unit,
    onObrisi: (Transakcija) -> Unit,
    onZatvori: () -> Unit,
    pocetniTip: TipTransakcije = TipTransakcije.TROSAK,
    fokusNaIznos: Boolean = false,
    /** Spremanje novog ili uređenog ponavljajućeg unosa; null skriva opciju ponavljanja. */
    onSpremiPonavljajuci: ((PonavljajuciUnos) -> Unit)? = null,
    /** Uređivanje postojećeg pravila ponavljanja (umjesto transakcije). */
    pravilo: PonavljajuciUnos? = null,
    /** Novo pravilo ponavljanja (otvoreno iz popisa ponavljajućih unosa). */
    novoPravilo: Boolean = false,
    onObrisiPonavljajuci: (PonavljajuciUnos) -> Unit = {},
    /** Nazivi računa za odabir (umjesto fiksnih načina plaćanja). */
    racuni: List<String> = NacinPlacanja.svi,
    /** Spremanje troška podijeljenog na više kategorija; null skriva opciju. */
    onSpremiPodijeljeno: ((List<Transakcija>) -> Unit)? = null,
    /** Prethodni unosi za prijedloge naziva, kategorije i iznosa. */
    zadnjiUnosi: List<TransakcijaSKategorijom> = emptyList(),
) {
    val ctx = LocalContext.current
    val zona = ZoneId.systemDefault()
    val radSPravilom = pravilo != null || novoPravilo
    var tip by remember { mutableStateOf(pravilo?.tip ?: postojeca?.tip ?: pocetniTip) }
    val fokusIznosa = remember { FocusRequester() }
    if (fokusNaIznos) LaunchedEffect(Unit) { runCatching { fokusIznosa.requestFocus() } }
    var iznos by remember { mutableStateOf((pravilo?.iznosCenti ?: postojeca?.iznosCenti)?.let { Novac.broj(it) } ?: "") }
    var kategorijaId by remember { mutableStateOf(pravilo?.kategorijaId ?: postojeca?.kategorijaId) }
    var opis by remember { mutableStateOf(pravilo?.opis ?: postojeca?.opis ?: "") }
    var nacin by remember {
        // Novi unos: zadani račun iz Postavki, inače Kartica.
        val zadani = hr.smartbudget.app.data.AppPostavke.get(ctx).stanje.value.zadaniRacun?.takeIf { it in racuni }
        mutableStateOf(pravilo?.nacinPlacanja ?: postojeca?.nacinPlacanja ?: zadani ?: racuni.firstOrNull { it == NacinPlacanja.KARTICA } ?: racuni.firstOrNull() ?: NacinPlacanja.KARTICA)
    }
    var slika by remember { mutableStateOf(postojeca?.slika) }
    var dijeli by remember { mutableStateOf(false) }
    // Dijelovi podijeljenog troška: (kategorija, iznos u obliku za unos)
    val dijelovi = remember { mutableStateListOf<Pair<Long?, String>>(null to "", null to "") }
    var prijedlogOdabran by remember { mutableStateOf(postojeca != null || pravilo != null) }
    var punaSlika by remember { mutableStateOf(false) }
    var kameraDatoteka by remember { mutableStateOf<java.io.File?>(null) }
    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        kameraDatoteka?.let { f -> if (ok) Fotografije.spremi(ctx, android.net.Uri.fromFile(f))?.let { slika = it }; f.delete() }
    }
    val galerija = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Fotografije.spremi(ctx, uri)?.let { slika = it }
    }
    var trenutak by remember {
        mutableStateOf(
            when {
                pravilo != null -> LocalDateTime.of(Ponavljanje.sljedeciDatum(pravilo), LocalTime.of(pravilo.vrijemeMinuta / 60, pravilo.vrijemeMinuta % 60))
                postojeca != null -> Instant.ofEpochMilli(postojeca.datumVrijeme).atZone(zona).toLocalDateTime()
                else -> LocalDateTime.now().withSecond(0).withNano(0)
            },
        )
    }
    var ponavljaj by remember { mutableStateOf(radSPravilom) }
    var aktivan by remember { mutableStateOf(pravilo?.aktivan ?: true) }
    var greska by remember { mutableStateOf<String?>(null) }
    var biramDatum by remember { mutableStateOf(false) }
    var biramVrijeme by remember { mutableStateOf(false) }
    var potvrdaBrisanja by remember { mutableStateOf(false) }

    val ponudjene = kategorije.filter { it.tip == tip }
    val bojaTipa = if (tip == TipTransakcije.PRIHOD) Neon.Zelena else Neon.Crvena

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(
            when {
                pravilo != null -> "Ponavljajući unos"
                novoPravilo -> "Novi ponavljajući unos"
                postojeca == null -> "Nova transakcija"
                else -> "Uredi transakciju"
            },
            onZatvori,
        )
        if (postojeca?.ponavljajuciId != null) {
            Text(
                "Ovaj unos je stvoren iz ponavljajućeg unosa. Promjena vrijedi samo za ovaj unos; pravilo se uređuje u Budžetima.",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
            )
        }
        Segmenti(
            listOf("Trošak", "Prihod"),
            if (tip == TipTransakcije.TROSAK) 0 else 1,
            {
                tip = if (it == 0) TipTransakcije.TROSAK else TipTransakcije.PRIHOD
                if (kategorije.firstOrNull { k -> k.id == kategorijaId }?.tip != tip) kategorijaId = null
            },
            boje = listOf(Neon.Crvena, Neon.Zelena),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Iznos (€)")
            NeonPolje(
                iznos, { iznos = it; greska = null },
                Modifier.fillMaxWidth().focusRequester(fokusIznosa),
                placeholder = "0,00",
                uCentima = true,
                stil = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = bojaTipa, fontFeatureSettings = "tnum"),
                visina = 64.dp,
                greska = greska != null,
                fokusBoja = bojaTipa,
            )
            greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
        }
        if (!dijeli) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Kategorija")
            if (ponudjene.isEmpty()) {
                Text("Nema kategorija ovog tipa. Dodajte ih u Budžetima.", style = Tip.malo)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ponudjene.forEach { k -> ChipKategorije(k, k.id == kategorijaId) { kategorijaId = k.id; greska = null } }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Naziv / napomena")
            NeonPolje(
                opis, { opis = it; prijedlogOdabran = false }, Modifier.fillMaxWidth(),
                placeholder = if (tip == TipTransakcije.PRIHOD) "npr. Plaća za listopad" else "npr. Tjedna nabava",
            )
            // Prijedlozi iz prethodnih unosa: naziv i kategorija jednim dodirom (iznos je svaki put drugačiji).
            val prijedlozi = if (prijedlogOdabran) emptyList() else prijedloziZaNaziv(zadnjiUnosi, opis, tip)
            if (prijedlozi.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    prijedlozi.forEach { pr ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(hexBoja(pr.kategorija.bojaHex).copy(alpha = .14f))
                                .clickable(onClickLabel = "Koristi prijedlog") {
                                    opis = pr.transakcija.opis
                                    kategorijaId = pr.kategorija.id
                                    prijedlogOdabran = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ikone.ikona(pr.kategorija.ikonaNaziv), null, Modifier.size(14.dp), tint = hexBoja(pr.kategorija.bojaHex).svjetlija())
                            Text(
                                " ${pr.transakcija.opis}",
                                color = Neon.TekstSvijetliSiv, fontSize = 12.sp, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // Naziv koji točno odgovara prethodnom unosu sam odabire kategoriju.
        LaunchedEffect(opis, tip) {
            if (kategorijaId == null && opis.isNotBlank()) {
                val n = Pretraga.normaliziraj(opis)
                zadnjiUnosi.firstOrNull { it.transakcija.tip == tip && Pretraga.normaliziraj(it.transakcija.opis) == n }
                    ?.let { kategorijaId = it.kategorija.id }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Račun")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (racuni + nacin).distinct().forEach { n -> NeonChip(n, nacin == n, { nacin = n }, boja = Neon.Cijan) }
            }
        }
        // Podjela na više kategorija (samo nova transakcija, ne pravilo)
        if (onSpremiPodijeljeno != null && postojeca == null && !radSPravilom && !ponavljaj) {
            RedakPrekidaca(
                naslov = "Podijeli na više kategorija",
                opis = "Npr. račun iz trgovine: dio namirnice, dio kućanstvo",
                ukljuceno = dijeli,
                ikona = "split",
            ) { dijeli = it }
            if (dijeli) {
                val ukupno = Novac.parsiraj(iznos) ?: 0L
                val rasporedeno = dijelovi.sumOf { Novac.parsiraj(it.second) ?: 0L }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dijelovi.forEachIndexed { i, (katId, iz) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var otvoren by remember { mutableStateOf(false) }
                            val k = ponudjene.firstOrNull { it.id == katId }
                            Box(Modifier.weight(1f)) {
                                GumbPolja(if (k != null) "check" else "chevDown", k?.naziv ?: "Kategorija", Modifier.fillMaxWidth()) { otvoren = true }
                                DropdownMenu(otvoren, { otvoren = false }, containerColor = Neon.Sheet) {
                                    ponudjene.forEach { kk ->
                                        DropdownMenuItem(
                                            text = { Text(kk.naziv, color = Neon.Tekst) },
                                            leadingIcon = { Icon(Ikone.ikona(kk.ikonaNaziv), null, Modifier.size(18.dp), tint = hexBoja(kk.bojaHex)) },
                                            onClick = { dijelovi[i] = kk.id to iz; otvoren = false },
                                        )
                                    }
                                }
                            }
                            NeonPolje(iz, { dijelovi[i] = katId to it; greska = null }, Modifier.width(120.dp), placeholder = "0,00", uCentima = true, visina = 52.dp)
                            if (dijelovi.size > 2) KruzniGumb("close", "Ukloni dio", { dijelovi.removeAt(i) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Raspoređeno ${Novac.eur(rasporedeno)} od ${Novac.eur(ukupno)}" +
                                if (ukupno - rasporedeno != 0L) " · ostalo ${Novac.stanje(ukupno - rasporedeno)}" else " ✓",
                            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
                            color = if (ukupno == rasporedeno && ukupno > 0) Neon.Zelena else Neon.TekstSiv,
                            modifier = Modifier.weight(1f),
                        )
                        TonalniGumb("Dio", { dijelovi.add(null to "") }, ikona = "plus", boja = Neon.Cijan)
                    }
                }
            }
        }
        // Fotografija računa
        if (!radSPravilom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OznakaPolja("Fotografija računa")
                val trenutna = slika
                if (trenutna != null) {
                    val bmp = remember(trenutna) { Fotografije.ucitaj(trenutna, 400)?.asImageBitmap() }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (bmp != null) {
                            Image(
                                bmp, "Fotografija računa",
                                Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Povećaj") { punaSlika = true },
                                contentScale = ContentScale.Crop,
                            )
                        }
                        TonalniGumb("Ukloni", { slika = null }, ikona = "trash", boja = Neon.Crvena)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalniGumb("Kamera", {
                            val (uri, f) = Fotografije.zaKameru(ctx)
                            kameraDatoteka = f
                            kamera.launch(uri)
                        }, Modifier.weight(1f), ikona = "camera", boja = Neon.Cijan)
                        TonalniGumb("Galerija", {
                            galerija.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, Modifier.weight(1f), ikona = "image", boja = Neon.Cijan)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (radSPravilom || ponavljaj) OznakaPolja(if (pravilo != null) "Sljedeći unos" else "Datum prvog unosa")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GumbPolja("calendar", HrDatum.puni(trenutak.toLocalDate()), Modifier.weight(1f)) { biramDatum = true }
                GumbPolja("clock", HrDatum.vrijeme(trenutak), Modifier.weight(1f)) { biramVrijeme = true }
            }
        }
        if (onSpremiPonavljajuci != null && postojeca == null) {
            val dan = trenutak.dayOfMonth
            RedakPrekidaca(
                naslov = if (pravilo != null) "Aktivan" else "Ponavljaj svaki mjesec",
                opis = when {
                    pravilo != null && !aktivan -> "Pauzirano: novi unosi se ne stvaraju"
                    dan > 28 -> "Svakog $dan. u mjesecu (u kraćim mjesecima zadnjeg dana)"
                    else -> "Svakog $dan. u mjesecu, isti iznos"
                },
                ukljuceno = if (pravilo != null) aktivan else ponavljaj,
                omoguceno = pravilo != null || !novoPravilo,
            ) { if (pravilo != null) aktivan = it else ponavljaj = it }
        }
        Spacer(Modifier.height(4.dp))
        PrimarniGumb(
            when {
                pravilo != null -> "Spremi ponavljajući unos"
                ponavljaj -> "Dodaj ponavljajući unos"
                postojeca == null -> "Dodaj transakciju"
                else -> "Spremi promjene"
            },
            onClick = {
            val centi = Novac.parsiraj(iznos)
            when {
                centi == null || centi <= 0 -> greska = "Unesite iznos veći od 0,00 €."
                dijeli && onSpremiPodijeljeno != null -> {
                    val iznosi = dijelovi.map { Novac.parsiraj(it.second) ?: 0L }
                    when {
                        dijelovi.any { it.first == null } -> greska = "Odaberite kategoriju za svaki dio."
                        iznosi.any { it <= 0 } -> greska = "Svaki dio mora imati iznos."
                        iznosi.sum() != centi -> greska = "Zbroj dijelova (${Novac.eur(iznosi.sum())}) mora biti jednak iznosu."
                        else -> {
                            val kada = trenutak.atZone(zona).toInstant().toEpochMilli()
                            onSpremiPodijeljeno(
                                dijelovi.mapIndexed { i, (katId, _) ->
                                    Transakcija(
                                        tip = tip, iznosCenti = iznosi[i], datumVrijeme = kada, kategorijaId = katId!!,
                                        opis = opis.trim(), nacinPlacanja = nacin, slika = if (i == 0) slika else null,
                                    )
                                },
                            )
                            onZatvori()
                        }
                    }
                }
                kategorijaId == null -> greska = "Odaberite kategoriju."
                ponavljaj && onSpremiPonavljajuci != null -> {
                    val d = trenutak.toLocalDate()
                    onSpremiPonavljajuci(
                        PonavljajuciUnos(
                            id = pravilo?.id ?: 0,
                            tip = tip,
                            iznosCenti = centi,
                            kategorijaId = kategorijaId!!,
                            opis = opis.trim(),
                            nacinPlacanja = nacin,
                            danUMjesecu = d.dayOfMonth,
                            vrijemeMinuta = trenutak.hour * 60 + trenutak.minute,
                            // Već stvoreni mjeseci se ne ponavljaju (zadnji ostaje), novi dan vrijedi od odabranog datuma.
                            pocetakEpochDan = d.toEpochDay(),
                            zadnjiEpochDan = pravilo?.zadnjiEpochDan,
                            aktivan = aktivan,
                        ),
                    )
                    onZatvori()
                }
                else -> {
                    onSpremi(
                        Transakcija(
                            id = postojeca?.id ?: 0,
                            tip = tip,
                            iznosCenti = centi,
                            datumVrijeme = trenutak.atZone(zona).toInstant().toEpochMilli(),
                            kategorijaId = kategorijaId!!,
                            opis = opis.trim(),
                            nacinPlacanja = nacin,
                            ponavljajuciId = postojeca?.ponavljajuciId,
                            grupaId = postojeca?.grupaId,
                            slika = slika,
                        ),
                    )
                    onZatvori()
                }
            }
        }, ikona = "check")
        if (postojeca != null) {
            GumbBrisanja("Obriši transakciju") { potvrdaBrisanja = true }
        }
        if (pravilo != null) {
            GumbBrisanja("Obriši ponavljajući unos") { potvrdaBrisanja = true }
        }
    }

    if (punaSlika) {
        slika?.let { put ->
            val bmp = remember(put) { Fotografije.ucitaj(put, 1600)?.asImageBitmap() }
            Dialog(onDismissRequest = { punaSlika = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable { punaSlika = false }, contentAlignment = Alignment.Center) {
                    if (bmp != null) Image(bmp, "Fotografija računa", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
    if (biramDatum) {
        val st = rememberDatePickerState(initialSelectedDateMillis = trenutak.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { biramDatum = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        trenutak = LocalDateTime.of(d, trenutak.toLocalTime())
                    }
                    biramDatum = false
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramDatum = false }) { Text("Odustani", color = Neon.TekstSiv) } },
            colors = DatePickerDefaults.colors(containerColor = Neon.Sheet),
        ) {
            DatePicker(st, colors = DatePickerDefaults.colors(containerColor = Neon.Sheet, selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu, todayDateBorderColor = Neon.Cijan, todayContentColor = Neon.Cijan))
        }
    }
    if (biramVrijeme) {
        val st = rememberTimePickerState(trenutak.hour, trenutak.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { biramVrijeme = false },
            containerColor = Neon.Sheet,
            confirmButton = {
                TextButton({
                    trenutak = LocalDateTime.of(trenutak.toLocalDate(), LocalTime.of(st.hour, st.minute))
                    biramVrijeme = false
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramVrijeme = false }) { Text("Odustani", color = Neon.TekstSiv) } },
            text = {
                TimePicker(
                    st,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Color(0xFF1A222D),
                        selectorColor = Neon.Zelena,
                        timeSelectorSelectedContainerColor = Neon.Zelena.copy(alpha = .2f),
                        timeSelectorSelectedContentColor = Neon.Zelena,
                    ),
                )
            },
        )
    }
    if (potvrdaBrisanja && pravilo != null) {
        PotvrdaBrisanja("Obrisati ponavljajući unos?", "Novi unosi se više neće stvarati. Već stvoreni unosi ostaju.", { potvrdaBrisanja = false }) {
            onObrisiPonavljajuci(pravilo)
            potvrdaBrisanja = false
            onZatvori()
        }
    }
    if (potvrdaBrisanja && postojeca != null) {
        PotvrdaBrisanja("Obrisati transakciju?", "Ova radnja se ne može poništiti.", { potvrdaBrisanja = false }) {
            onObrisi(postojeca)
            potvrdaBrisanja = false
            onZatvori()
        }
    }
}

@Composable
fun RedakPrekidaca(naslov: String, opis: String, ukljuceno: Boolean, omoguceno: Boolean = true, ikona: String = "repeat", onPromjena: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, if (ukljuceno) Neon.Ljubicasta.copy(alpha = .5f) else Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(enabled = omoguceno, role = Role.Switch) { onPromjena(!ukljuceno) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ikone.ui(ikona), null, Modifier.size(20.dp), tint = if (ukljuceno) Neon.LjubicastaTekst else Neon.TekstSiv)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(naslov, style = Tip.tijelo.copy(fontSize = 14.sp))
            Text(opis, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
        Switch(
            ukljuceno, onPromjena, enabled = omoguceno,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Neon.Ljubicasta, checkedThumbColor = Neon.Tekst,
                uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = Neon.TekstSiv, uncheckedBorderColor = Color(0x33FFFFFF),
                disabledCheckedTrackColor = Neon.Ljubicasta.copy(alpha = .6f), disabledCheckedThumbColor = Neon.Tekst,
            ),
        )
    }
}

@Composable
fun PotvrdaBrisanja(naslov: String, tekst: String, onOdustani: () -> Unit, onPotvrdi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOdustani,
        containerColor = Neon.Sheet,
        title = { Text(naslov, color = Neon.Tekst) },
        text = { Text(tekst, color = Neon.TekstSvijetliSiv) },
        confirmButton = { TextButton(onPotvrdi) { Text("Obriši", color = Neon.Crvena, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onOdustani) { Text("Odustani", color = Neon.TekstSiv) } },
    )
}

@Composable
fun GumbBrisanja(tekst: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Neon.Crvena.copy(alpha = .4f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ikone.ui("trash"), null, Modifier.size(18.dp), tint = Neon.Crvena)
        Spacer(Modifier.width(8.dp))
        Text(tekst, color = Neon.Crvena, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GumbPolja(ikona: String, tekst: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ikone.ui(ikona), null, Modifier.size(18.dp), tint = Neon.Cijan)
        Text(tekst, style = Tip.tijelo, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KategorijaSheet(
    postojeca: Kategorija?,
    onSpremi: (Kategorija) -> Unit,
    onObrisi: (Kategorija) -> Unit,
    onZatvori: () -> Unit,
) {
    var naziv by remember { mutableStateOf(postojeca?.naziv ?: "") }
    var tip by remember { mutableStateOf(postojeca?.tip ?: TipTransakcije.TROSAK) }
    var ikona by remember { mutableStateOf(postojeca?.ikonaNaziv ?: "cart") }
    var boja by remember { mutableStateOf(postojeca?.bojaHex ?: "#9D4EDD") }
    var limit by remember { mutableStateOf(postojeca?.mjesecniLimitCenti?.let { Novac.broj(it) } ?: "") }
    var greska by remember { mutableStateOf<String?>(null) }
    var potvrda by remember { mutableStateOf(false) }
    val c = hexBoja(boja)

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(if (postojeca == null) "Nova kategorija" else "Uredi kategoriju", onZatvori)
        // Pregled
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Neon.Ispuna)
                .border(1.dp, Neon.RubSlab, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlocicaIkone(Ikone.ikona(ikona), c, sjaj = true)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Pregled", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                hr.smartbudget.app.ui.PillKategorije(Kategorija(naziv = naziv.ifBlank { "Naziv kategorije" }, bojaHex = boja, ikonaNaziv = ikona, tip = tip))
            }
        }
        Segmenti(
            listOf("Trošak", "Prihod"),
            if (tip == TipTransakcije.TROSAK) 0 else 1,
            { tip = if (it == 0) TipTransakcije.TROSAK else TipTransakcije.PRIHOD },
            boje = listOf(Neon.Crvena, Neon.Zelena),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Naziv kategorije")
            NeonPolje(naziv, { naziv = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "npr. Odjeća i obuća", greska = greska != null && naziv.isBlank(), fokusBoja = c)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Ikona")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 6,
            ) {
                Ikone.nazivi.forEach { n ->
                    val on = n == ikona
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .then(if (on) Modifier.neonSjaj(c, 14.dp, 14.dp, .5f) else Modifier)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.copy(alpha = .24f) else Color(0x0AFFFFFF))
                            .border(1.dp, if (on) c else Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { ikona = n },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Ikone.ikona(n), null, Modifier.size(22.dp), tint = if (on) c.svjetlija() else Neon.TekstSrednji)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Neonska boja")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Neon.paleta.forEach { hex ->
                    val on = hex.equals(boja, ignoreCase = true)
                    val b = hexBoja(hex)
                    Box(
                        Modifier.size(width = 30.dp, height = 44.dp).clickable(role = Role.RadioButton) { boja = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .neonSjaj(b, if (on) 14.dp else 6.dp, 13.dp, if (on) .8f else .4f)
                                .clip(CircleShape)
                                .background(b)
                                .then(if (on) Modifier.border(2.dp, Neon.Tekst, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Ikone.ui("check"), null, Modifier.size(14.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
        if (tip == TipTransakcije.TROSAK) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        OznakaPolja("Limit po ciklusu (€)")
                        Text("Prazno = bez limita", style = Tip.malo.copy(fontSize = 12.sp))
                    }
                    NeonPolje(
                        limit, { limit = it; greska = null },
                        Modifier.width(140.dp),
                        placeholder = "—",
                        uCentima = true,
                        stil = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
                        visina = 44.dp,
                        fokusBoja = c,
                    )
                }
                val vrijednost = (Novac.parsiraj(limit) ?: 0L) / 100f
                Slider(
                    value = vrijednost.coerceIn(0f, 1000f),
                    onValueChange = { limit = Novac.broj((it / 10f).toLong() * 1000L).let { v -> if (v == "0,00") "" else v } },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(thumbColor = Neon.Tekst, activeTrackColor = c, inactiveTrackColor = Color(0x1AFFFFFF)),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0 €", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                    Text("1.000 €", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                }
                Novac.parsiraj(limit)?.takeIf { it > 0 }?.let {
                    Text("Upozorenje pri 80 % limita (${Novac.eur(it * 8 / 10)}) i pri prekoračenju.", style = Tip.malo.copy(fontSize = 12.sp))
                }
            }
        }
        greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
        PrimarniGumb("Spremi kategoriju", onClick = {
            val lim = if (limit.isBlank()) null else Novac.parsiraj(limit)
            when {
                naziv.isBlank() -> greska = "Unesite naziv kategorije."
                limit.isNotBlank() && (lim == null || lim < 0) -> greska = "Limit mora biti iznos, npr. 150,00."
                else -> {
                    onSpremi(
                        Kategorija(
                            id = postojeca?.id ?: 0,
                            naziv = naziv.trim(),
                            bojaHex = boja,
                            ikonaNaziv = ikona,
                            tip = tip,
                            mjesecniLimitCenti = if (tip == TipTransakcije.TROSAK) lim?.takeIf { it > 0 } else null,
                        ),
                    )
                    onZatvori()
                }
            }
        }, ikona = "check")
        if (postojeca != null) GumbBrisanja("Obriši kategoriju") { potvrda = true }
    }
    if (potvrda && postojeca != null) {
        PotvrdaBrisanja("Obrisati kategoriju?", "Kategorija \"${postojeca.naziv}\" bit će obrisana ako nema transakcija.", { potvrda = false }) {
            onObrisi(postojeca)
            potvrda = false
            onZatvori()
        }
    }
}
