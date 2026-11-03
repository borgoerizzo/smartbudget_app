package hr.smartbudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.domain.CsvTablica
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.UvozCsv
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uvoz izvoda iz banke (CSV): odabir datoteke, pa prozor za stupce, račun i kategorije. */
@Composable
fun UvozKartica(vm: BudgetViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tablica by remember { mutableStateOf<CsvTablica?>(null) }
    val odabir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bajtovi = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    // UTF-8, a ako ima neispravnih znakova, Windows-1250 (česti izvozi hrvatskih banaka)
                    val utf = bajtovi.toString(Charsets.UTF_8)
                    UvozCsv.parsiraj(if ('�' in utf) bajtovi.toString(charset("windows-1250")) else utf)
                }
            }.onSuccess { t ->
                if (t.zaglavlje.isEmpty() || t.redovi.isEmpty()) vm.poruka("Datoteka je prazna ili nije CSV.") else tablica = t
            }.onFailure { vm.poruka("Datoteku nije moguće pročitati: ${it.message}") }
        }
    }

    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlocicaIkone(Ikone.ui("upload"), Neon.Cijan)
            Column(Modifier.weight(1f)) {
                Text("Uvoz izvoda iz banke", style = Tip.naslovKartice)
                Text("CSV datoteka; duplikati se preskaču", style = Tip.malo)
            }
        }
        TonalniGumb(
            "Odaberi CSV datoteku",
            { odabir.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
            Modifier.fillMaxWidth(), ikona = "upload", boja = Neon.Cijan, visina = 48.dp,
        )
    }
    tablica?.let { t -> UvozDijalog(t, vm) { tablica = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UvozDijalog(t: CsvTablica, vm: BudgetViewModel, onZatvori: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kategorije by vm.kategorije.collectAsState()
    val racuni by vm.racuni.collectAsState()
    val (d0, i0, o0) = remember(t) { UvozCsv.pogodiStupce(t.zaglavlje) }
    var stDatum by remember { mutableStateOf(d0) }
    var stIznos by remember { mutableStateOf(i0) }
    var stOpis by remember { mutableStateOf(o0) }
    var racun by remember { mutableStateOf(racuni.firstOrNull()?.naziv ?: "Tekući račun") }
    var katTrosak by remember { mutableStateOf(kategorije.firstOrNull { it.tip == TipTransakcije.TROSAK }) }
    var katPrihod by remember { mutableStateOf(kategorije.firstOrNull { it.tip == TipTransakcije.PRIHOD }) }
    var radim by remember { mutableStateOf(false) }

    val redovi = remember(t, stDatum, stIznos, stOpis) {
        t.redovi.mapNotNull { r ->
            val d = r.getOrNull(stDatum)?.let(UvozCsv::datum) ?: return@mapNotNull null
            val c = r.getOrNull(stIznos)?.let(UvozCsv::iznos) ?: return@mapNotNull null
            BudgetRepository.UvozRed(d, c, r.getOrNull(stOpis).orEmpty())
        }
    }
    val neispravnih = t.redovi.size - redovi.size

    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Neon.Pozadina).systemBarsPadding()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uvoz izvoda", style = Tip.naslov, modifier = Modifier.weight(1f))
                    KruzniGumb("close", "Zatvori", onZatvori)
                }
                Text("${t.redovi.size} redaka u datoteci · ${redovi.size} ispravnih" + if (neispravnih > 0) " · $neispravnih se ne mogu pročitati" else "", style = Tip.malo)
                BiracStupca("Stupac s datumom", t.zaglavlje, stDatum) { stDatum = it }
                BiracStupca("Stupac s iznosom (minus = trošak)", t.zaglavlje, stIznos) { stIznos = it }
                BiracStupca("Stupac s opisom", t.zaglavlje, stOpis) { stOpis = it }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OznakaPolja("Račun")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        racuni.forEach { r -> NeonChip(r.naziv, racun == r.naziv, { racun = r.naziv }, boja = Neon.Cijan) }
                    }
                }
                BiracKategorije("Kategorija za troškove", kategorije.filter { it.tip == TipTransakcije.TROSAK }, katTrosak) { katTrosak = it }
                BiracKategorije("Kategorija za prihode", kategorije.filter { it.tip == TipTransakcije.PRIHOD }, katPrihod) { katPrihod = it }
                Text("Pregled (prvih 5)", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
                Column {
                    redovi.take(5).forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.opis.ifBlank { "(bez opisa)" }, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1)
                                Text(HrDatum.puni(r.datum), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                            }
                            Text(Novac.predznak(r.centi), color = if (r.centi < 0) Neon.Crvena else Neon.Zelena, style = Tip.iznos.copy(fontSize = 14.sp))
                        }
                    }
                }
                PrimarniGumb(
                    if (radim) "Uvozim…" else "Uvezi ${redovi.size} transakcija",
                    onClick = {
                        val kt = katTrosak
                        val kp = katPrihod
                        if (kt == null || kp == null) { vm.poruka("Odaberite kategorije za troškove i prihode."); return@PrimarniGumb }
                        radim = true
                        scope.launch {
                            val (n, dup) = withContext(Dispatchers.IO) { BudgetRepository.get(ctx).uvezi(redovi, racun, kt.id, kp.id) }
                            vm.poruka("Uvezeno $n transakcija" + if (dup > 0) ", preskočeno $dup duplikata" else "")
                            onZatvori()
                        }
                    },
                    ikona = "upload", omogucen = redovi.isNotEmpty() && !radim,
                )
            }
        }
    }
}

@Composable
private fun BiracStupca(naslov: String, zaglavlje: List<String>, odabran: Int, onOdabir: (Int) -> Unit) {
    var otvoren by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja(naslov)
        Box {
            GumbPolja("chevDown", zaglavlje.getOrNull(odabran) ?: "—", Modifier.fillMaxWidth()) { otvoren = true }
            DropdownMenu(otvoren, { otvoren = false }, containerColor = Neon.Sheet) {
                zaglavlje.forEachIndexed { i, h ->
                    DropdownMenuItem(text = { Text(h.ifBlank { "Stupac ${i + 1}" }, color = Neon.Tekst) }, onClick = { onOdabir(i); otvoren = false })
                }
            }
        }
    }
}

@Composable
private fun BiracKategorije(naslov: String, kategorije: List<Kategorija>, odabrana: Kategorija?, onOdabir: (Kategorija) -> Unit) {
    var otvoren by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja(naslov)
        Box {
            GumbPolja("chevDown", odabrana?.naziv ?: "Odaberite", Modifier.fillMaxWidth()) { otvoren = true }
            DropdownMenu(otvoren, { otvoren = false }, containerColor = Neon.Sheet) {
                kategorije.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k.naziv, color = Neon.Tekst) },
                        leadingIcon = { Icon(Ikone.ikona(k.ikonaNaziv), null, Modifier.size(18.dp), tint = hexBoja(k.bojaHex)) },
                        onClick = { onOdabir(k); otvoren = false },
                    )
                }
            }
        }
    }
}
