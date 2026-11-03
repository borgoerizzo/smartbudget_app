package hr.smartbudget.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.BackupManager
import hr.smartbudget.app.data.SadrzajKopije
import hr.smartbudget.app.data.UcestalostBackupa
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.ui.BudgetViewModel
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Postavke sigurnosne kopije: mapa, automatski backup (svaki unos / tjedno), kopija i vraćanje na zahtjev. */
@Composable
fun BackupKartica(vm: BudgetViewModel) {
    val ctx = LocalContext.current
    val backup = remember { BackupManager.get(ctx) }
    val s by backup.stanje.collectAsState()
    val scope = rememberCoroutineScope()
    var ukljuciNakonOdabira by remember { mutableStateOf(false) }
    var zaVratiti by remember { mutableStateOf<SadrzajKopije?>(null) }

    val odabirMape = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { backup.postaviMapu(uri) }
                .onSuccess {
                    if (ukljuciNakonOdabira) backup.postaviUkljucen(true)
                    // Prva kopija odmah, da korisnik vidi da radi.
                    scope.launch { vm.poruka(withContext(Dispatchers.IO) { backup.napraviKopiju() }) }
                }
                .onFailure { vm.poruka("Mapu nije moguće koristiti: ${it.message}") }
        }
        ukljuciNakonOdabira = false
    }
    val odabirKopije = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { backup.procitajKopiju(uri) } }
                .onSuccess { zaVratiti = it }
                .onFailure { vm.poruka("Datoteku nije moguće pročitati: ${it.message}") }
        }
    }

    Kartica(razmak = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlocicaIkone(Ikone.ui("download"), Neon.LjubicastaSvijetla)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Sigurnosna kopija", style = Tip.naslovKartice)
                Text("Svi podaci u JSON datoteci u odabranoj mapi", style = Tip.malo)
            }
        }

        // Mapa
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { odabirMape.launch(s.mapa) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mapa za kopije", style = Tip.tijelo.copy(fontSize = 14.sp))
                Text(
                    s.nazivMape ?: if (s.mapa != null) "Odabrana" else "Nije odabrana · dodirnite za odabir",
                    style = Tip.malo, color = if (s.mapa == null) Neon.Jantar else Neon.TekstSiv,
                )
            }
            Text("Promijeni", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // Automatski backup
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatski backup", style = Tip.tijelo.copy(fontSize = 14.sp))
                Text(if (s.ukljucen) s.ucestalost.naziv else "Isključeno", style = Tip.malo)
            }
            Switch(
                s.ukljucen,
                { ukljuci ->
                    if (ukljuci && s.mapa == null) {
                        ukljuciNakonOdabira = true
                        odabirMape.launch(null)
                    } else {
                        backup.postaviUkljucen(ukljuci)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Neon.Zelena, checkedThumbColor = Neon.NaGumbu,
                    uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = Neon.TekstSiv, uncheckedBorderColor = Color(0x33FFFFFF),
                ),
            )
        }
        if (s.ukljucen) {
            // Treća opcija "Sada" ne mijenja učestalost, nego odmah radi kopiju.
            Segmenti(
                UcestalostBackupa.entries.map { it.kratko } + (if (s.radi) "Spremam…" else "Sada"),
                s.ucestalost.ordinal,
                { i ->
                    if (i < UcestalostBackupa.entries.size) backup.postaviUcestalost(UcestalostBackupa.entries[i])
                    else if (!s.radi) scope.launch { vm.poruka(withContext(Dispatchers.IO) { backup.napraviKopiju() }) }
                },
            )
        }

        // Status
        val zadnji = s.zadnji?.let {
            val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
            "Zadnja kopija: ${HrDatum.puni(t.toLocalDate())} u ${HrDatum.vrijeme(t)}"
        } ?: "Još nema kopije"
        Text(zadnji, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        s.greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp, lineHeight = 16.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalniGumb(
                if (s.radi) "Spremam…" else "Kopiraj",
                {
                    if (s.mapa == null) odabirMape.launch(null)
                    else if (!s.radi) scope.launch { vm.poruka(withContext(Dispatchers.IO) { backup.napraviKopiju() }) }
                },
                Modifier.weight(1f), ikona = "download", visina = 48.dp,
            )
            TonalniGumb(
                "Vrati",
                { odabirKopije.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                Modifier.weight(1f), ikona = "restore", boja = Neon.Cijan, visina = 48.dp,
            )
        }
        Text(
            "Automatske kopije: čuva se zadnjih ${BackupManager.ZADRZI}, starije se brišu. Ručne kopije se ne brišu.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
        )
    }

    zaVratiti?.let { k ->
        val kada = runCatching { java.time.OffsetDateTime.parse(k.vrijeme).toLocalDateTime() }.getOrNull()
        AlertDialog(
            onDismissRequest = { zaVratiti = null },
            containerColor = Neon.Sheet,
            title = { Text("Vratiti podatke iz kopije?", color = Neon.Tekst) },
            text = {
                Text(
                    (kada?.let { "Kopija od ${HrDatum.puni(it.toLocalDate())} u ${HrDatum.vrijeme(it)}" } ?: "Kopija") +
                        " (verzija ${k.verzijaAplikacije}):\n" +
                        "• ${k.transakcije.size} transakcija\n• ${k.kategorije.size} kategorija\n• ${k.ponavljajuci.size} ponavljajućih unosa\n\n" +
                        "Svi trenutni podaci bit će ZAMIJENJENI sadržajem kopije.",
                    color = Neon.TekstSvijetliSiv,
                )
            },
            confirmButton = {
                TextButton({
                    zaVratiti = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { backup.vrati(k) } }
                            .onSuccess { vm.poruka("Podaci vraćeni iz kopije (${k.transakcije.size} transakcija)") }
                            .onFailure { vm.poruka("Vraćanje nije uspjelo: ${it.message}") }
                    }
                }) { Text("Zamijeni podatke", color = Neon.Crvena, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ zaVratiti = null }) { Text("Odustani", color = Neon.TekstSiv) } },
        )
    }
}
