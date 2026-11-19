package hr.smartbudget.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import hr.smartbudget.app.BuildConfig
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.domain.Ciklus
import hr.smartbudget.app.domain.CycleManager
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.export.FormatIzvoza
import hr.smartbudget.app.ui.BudgetViewModel
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.NeonChip
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import hr.smartbudget.app.widget.StanjeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class Razdoblje(val naziv: String) { AKTIVNI("Aktivni ciklus"), PROSLI("Prošli ciklus"), GODINA("Godina"), PRILAGODENO("Prilagođeno") }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PostavkeEkran(vm: BudgetViewModel) {
    val postavke by vm.postavke.collectAsState()
    val kategorije by vm.kategorije.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var format by remember { mutableStateOf(FormatIzvoza.XLSX) }
    var razdoblje by remember { mutableStateOf(Razdoblje.AKTIVNI) }
    var od by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var doDana by remember { mutableStateOf(LocalDate.now()) }
    var iskljucene by remember { mutableStateOf(emptySet<Long>()) }
    var biramOd by remember { mutableStateOf<Boolean?>(null) }
    var oProgramu by remember { mutableStateOf(false) }
    if (oProgramu) OProgramuDijalog { oProgramu = false }

    val (rOd, rDo) = when (razdoblje) {
        Razdoblje.AKTIVNI -> vm.ciklusOdDanas(0).let { it.pocetak to it.kraj }
        Razdoblje.PROSLI -> vm.ciklusOdDanas(-1).let { it.pocetak to it.kraj }
        Razdoblje.GODINA -> LocalDate.now().withDayOfYear(1) to LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear())
        Razdoblje.PRILAGODENO -> od to doDana
    }
    val odabrane = kategorije.map { it.id }.filter { it !in iskljucene }.toSet()
    val nazivDatoteke = "SmartBudget_${rOd}_${rDo}.${format.nastavak}"

    suspend fun izvezi(out: java.io.OutputStream) = withContext(Dispatchers.IO) { vm.izvezi(format, rOd, rDo, odabrane, out) }

    val launcherXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatIzvoza.XLSX.mime)) { uri ->
        if (uri != null) scope.launch {
            runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { izvezi(it) } }
                .onSuccess { vm.poruka("Izvezeno $it transakcija u Excel (.xlsx)") }
                .onFailure { vm.poruka("Izvoz nije uspio: ${it.message}") }
        }
    }
    val launcherCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatIzvoza.CSV.mime)) { uri ->
        if (uri != null) scope.launch {
            runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { izvezi(it) } }
                .onSuccess { vm.poruka("Izvezeno $it transakcija u CSV") }
                .onFailure { vm.poruka("Izvoz nije uspio: ${it.message}") }
        }
    }

    fun podijeli() = scope.launch {
        runCatching {
            val dir = File(ctx.cacheDir, "izvoz").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, nazivDatoteke)
            f.outputStream().use { izvezi(it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SmartBudget izvoz ${HrDatum.srednji(rOd)} – ${HrDatum.srednji(rDo)}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Podijeli izvoz").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { vm.poruka("Dijeljenje nije uspjelo: ${it.message}") }
    }

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Postavke", style = Tip.naslovVeliki) }
        item { NaslovGrupe("Unos") }
        item {
            val racuni by vm.racuni.collectAsState()
            val appPostavke = hr.smartbudget.app.data.AppPostavke.get(androidx.compose.ui.platform.LocalContext.current)
            val ap by appPostavke.stanje.collectAsState()
            val nazivi = racuni.map { it.naziv }
            val zadani = ap.zadaniRacun?.takeIf { it in nazivi } ?: nazivi.firstOrNull { it == "Kartica" } ?: nazivi.firstOrNull()
            Kartica(razmak = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlocicaIkone(Ikone.ui("wallet"), Neon.Cijan)
                    Column {
                        Text("Zadani račun", style = Tip.naslovKartice)
                        Text("Unaprijed odabran pri svakom novom unosu", style = Tip.malo)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nazivi.forEach { n -> NeonChip(n, n == zadani, { appPostavke.postaviZadaniRacun(n) }, boja = Neon.Cijan) }
                }
            }
        }
        item { NaslovGrupe("Podaci") }
        item {
            Kartica(razmak = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlocicaIkone(Ikone.ui("download"), Neon.Zelena)
                    Column {
                        Text("Izvoz podataka", style = Tip.naslovKartice)
                        Text("Excel (.xlsx) ili CSV", style = Tip.malo)
                    }
                }
                Segmenti(FormatIzvoza.entries.map { it.naziv }, format.ordinal, { format = FormatIzvoza.entries[it] })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OznakaPolja("Razdoblje")
                    // Opcije razdoblja: po dvije u retku, sve iste širine.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Razdoblje.entries.chunked(2).forEach { par ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                par.forEach { r -> NeonChip(r.naziv, razdoblje == r, { razdoblje = r }, Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Početni i završni datum: isti izgled, puna širina, u jednom redu.
                    GumbPolja("calendar", "Od   ${HrDatum.puni(rOd)}", Modifier.fillMaxWidth()) {
                        razdoblje = Razdoblje.PRILAGODENO; od = rOd; doDana = rDo; biramOd = true
                    }
                    GumbPolja("calendar", "Do   ${HrDatum.puni(rDo)}", Modifier.fillMaxWidth()) {
                        razdoblje = Razdoblje.PRILAGODENO; od = rOd; doDana = rDo; biramOd = false
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        OznakaPolja("Kategorije")
                        Spacer(Modifier.weight(1f))
                        Text("${odabrane.size} od ${kategorije.size} odabrano", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        kategorije.forEach { k ->
                            ChipOdabira(k, k.id in odabrane) {
                                iskljucene = if (k.id in iskljucene) iskljucene - k.id else iskljucene + k.id
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimarniGumb(
                        if (format == FormatIzvoza.XLSX) "Izvezi u Excel" else "Izvezi u CSV",
                        onClick = {
                            if (rDo.isBefore(rOd)) vm.poruka("Datum \"Do\" je prije datuma \"Od\".")
                            else if (format == FormatIzvoza.XLSX) launcherXlsx.launch(nazivDatoteke) else launcherCsv.launch(nazivDatoteke)
                        },
                        ikona = "download",
                        omogucen = odabrane.isNotEmpty(),
                    )
                    TonalniGumb("Podijeli", { if (odabrane.isNotEmpty()) podijeli() }, Modifier.fillMaxWidth(), ikona = "share", boja = Neon.Cijan, visina = 48.dp)
                    Text(
                        "${HrDatum.srednji(rOd)} – ${HrDatum.srednji(rDo)} · ${odabrane.size} kategorija · birate mapu za spremanje",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item { UvozKartica(vm) }
        item { BackupKartica(vm) }
        item { NaslovGrupe("Sigurnost i obavijesti") }
        item { SigurnostKartica() }
        item { ObavijestiKartica(vm) }
        item { NaslovGrupe("Općenito") }
        item {
            Kartica(padding = 0.dp, razmak = 0.dp) {
                run {
                    val appP = hr.smartbudget.app.data.AppPostavke.get(ctx)
                    val st by appP.stanje.collectAsState()
                    var biram by remember { mutableStateOf(false) }
                    val v = hr.smartbudget.app.domain.Valute.po(st.valuta)
                    RedakPostavke("euro", "Valuta aplikacije", "${v.naziv} (${v.simbol}) · dodirnite za promjenu") { biram = true }
                    if (biram) BiracValuteDijalog(
                        "Valuta aplikacije", hr.smartbudget.app.domain.Valute.fiat, st.valuta,
                        "Svi unosi, limiti i stanja vode se u ovoj valuti. Postojeći iznosi se ne preračunavaju, mijenja se samo oznaka. " +
                            "Štednju u drugim valutama i kriptovalutama dodajte u Budžeti → Štednja u valutama.",
                        { appP.postaviValutu(it.kod); biram = false; vm.osvjeziWidget() },
                    ) { biram = false }
                }
                Razdjelnik()
                run {
                    var provjeravam by remember { mutableStateOf(false) }
                    var nova by remember { mutableStateOf<hr.smartbudget.app.data.NovaVerzija?>(null) }
                    // Rezultat zadnje provjere ostaje na samoj pločici, u boji, da se ne previdi.
                    var rezultat by remember { mutableStateOf<Pair<String, Color>?>(null) }
                    RedakPostavke(
                        "download", "Provjeri ažuriranja",
                        when {
                            provjeravam -> "Provjeravam…"
                            rezultat != null -> rezultat!!.first
                            else -> "Instalirana verzija ${BuildConfig.VERSION_NAME} · dodirnite za provjeru"
                        },
                        opisBoja = if (provjeravam) Neon.Cijan else rezultat?.second,
                    ) {
                        if (!provjeravam) scope.launch {
                            provjeravam = true
                            runCatching { hr.smartbudget.app.data.Azuriranja.provjeri() }
                                .onSuccess { n ->
                                    rezultat = if (n == null) "✓ Imate najnoviju verziju (${BuildConfig.VERSION_NAME})" to Neon.Zelena
                                    else "Dostupna je nova verzija ${n.naziv} · dodirnite za instalaciju" to Neon.Cijan
                                    nova = n
                                }
                                .onFailure { rezultat = "Provjera nije uspjela: ${it.message ?: "nema veze s internetom"}" to Neon.Crvena }
                            provjeravam = false
                        }
                    }
                    nova?.let { AzuriranjeDijalog(it) { nova = null } }
                }
                Razdjelnik()
                RedakPostavke("navGrid", "Widget na početnom zaslonu", "Stanje računa i brzi unos · dodirnite za dodavanje") {
                    if (!StanjeWidget.zatraziDodavanje(ctx)) vm.poruka("Pokretač ne podržava automatsko dodavanje. Dugo pritisnite početni zaslon → Widgeti → SmartBudget.")
                }
                Razdjelnik()
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlocicaIkone(Ikone.ui("calendar"), Neon.Cijan, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Dan početka ciklusa", style = Tip.tijelo)
                        Text("Npr. dan isplate plaće", style = Tip.malo)
                    }
                    KruzniGumb("chevLeft", "Smanji dan početka ciklusa", { vm.postaviPocetniDan(if (postavke.pocetniDanUCiklusu <= 1) 31 else postavke.pocetniDanUCiklusu - 1) })
                    Text(
                        "${postavke.pocetniDanUCiklusu}.", style = Tip.iznos.copy(fontSize = 18.sp), color = Neon.Tekst,
                        modifier = Modifier.width(44.dp), textAlign = TextAlign.Center,
                    )
                    KruzniGumb("chevRight", "Povećaj dan početka ciklusa", { vm.postaviPocetniDan(if (postavke.pocetniDanUCiklusu >= 31) 1 else postavke.pocetniDanUCiklusu + 1) })
                }
                Razdjelnik()
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlocicaIkone(Ikone.ui("clock"), Neon.Ljubicasta, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Kraći mjeseci", style = Tip.tijelo)
                        Text(
                            if (postavke.nacinObradeKrajaMjeseca) "Ciklus počinje zadnjeg dana mjeseca" else "Ciklus počinje 1. sljedećeg mjeseca",
                            style = Tip.malo,
                        )
                    }
                    Switch(
                        postavke.nacinObradeKrajaMjeseca, { vm.postaviKrajMjeseca(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Neon.Zelena, checkedThumbColor = Neon.NaGumbu,
                            uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = Neon.TekstSiv, uncheckedBorderColor = Color(0x33FFFFFF),
                        ),
                    )
                }
                val cm by vm.cycleManager.collectAsState()
                val c = cm.ciklusZa(LocalDate.now())
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Text(opisCiklusa(c, postavke.pocetniDanUCiklusu, postavke.nacinObradeKrajaMjeseca), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp))
                }
            }
        }
        item {
            // Dodir na verziju otvara "O programu".
            Text(
                "SmartBudget · verzija ${BuildConfig.VERSION_NAME}",
                color = Neon.Neaktivno, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "O programu") { oProgramu = true }
                    .padding(vertical = 8.dp),
            )
        }
    }

    biramOd?.let { jeOd ->
        val pocetni = if (jeOd) od else doDana
        val st = rememberDatePickerState(initialSelectedDateMillis = pocetni.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { biramOd = null },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (jeOd) od = d else doDana = d
                    }
                    biramOd = null
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramOd = null }) { Text("Odustani", color = Neon.TekstSiv) } },
            colors = DatePickerDefaults.colors(containerColor = Neon.Sheet),
        ) {
            DatePicker(st, colors = DatePickerDefaults.colors(containerColor = Neon.Sheet, selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu, todayDateBorderColor = Neon.Cijan, todayContentColor = Neon.Cijan))
        }
    }
}

private fun opisCiklusa(c: Ciklus, dan: Int, zadnji: Boolean): String {
    val sljedeci = c.kraj.plusDays(1)
    return "Aktivni ciklus: ${HrDatum.raspon(c)}. Sljedeći počinje ${HrDatum.kratko(sljedeci)}." +
        if (dan > 28) {
            if (zadnji) " Ako mjesec nema $dan. dan, ciklus počinje zadnjeg dana u mjesecu."
            else " Ako mjesec nema $dan. dan, ciklus počinje 1. u sljedećem mjesecu."
        } else ""
}

@Composable
private fun NaslovGrupe(t: String) {
    Text(t, style = Tip.naslovKartice.copy(fontSize = 15.sp, color = Neon.TekstSvijetliSiv), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun Razdjelnik() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0FFFFFFF)))
}

@Composable
private fun RedakPostavke(ikona: String, naziv: String, opis: String, opisBoja: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlocicaIkone(Ikone.ui(ikona), Neon.Jantar, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(naziv, style = Tip.tijelo)
            Text(
                opis,
                style = if (opisBoja != null) Tip.malo.copy(color = opisBoja, fontWeight = FontWeight.SemiBold) else Tip.malo,
            )
        }
    }
}

@Composable
private fun ChipOdabira(k: Kategorija, odabran: Boolean, onClick: () -> Unit) {
    val boja = hexBoja(k.bojaHex)
    val oblik = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(oblik)
            .background(if (odabran) boja.copy(alpha = .13f) else Color(0x08FFFFFF))
            .border(1.dp, if (odabran) boja.copy(alpha = .6f) else Color(0x1AFFFFFF), oblik)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (odabran) boja else Neon.Neaktivno))
        Spacer(Modifier.width(8.dp))
        Text(k.naziv, color = if (odabran) Neon.Tekst else Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (odabran) {
            Spacer(Modifier.width(6.dp))
            Icon(Ikone.ui("check"), null, Modifier.size(14.dp), tint = boja)
        }
    }
}
