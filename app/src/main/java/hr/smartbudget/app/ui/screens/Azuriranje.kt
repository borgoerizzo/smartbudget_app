package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import hr.smartbudget.app.BuildConfig
import hr.smartbudget.app.data.Azuriranja
import hr.smartbudget.app.data.NovaVerzija
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import kotlinx.coroutines.launch
import java.io.File

/**
 * Nova verzija: popis izmjena, preuzimanje i instalacija. Obavezno ažuriranje (promjena za 0,1)
 * nema gumb "Kasnije" i ne zatvara se dodirom izvan prozora ni tipkom Natrag.
 */
@Composable
fun AzuriranjeDijalog(n: NovaVerzija, onKasnije: () -> Unit) {
    val ctx = LocalContext.current
    val opseg = rememberCoroutineScope()
    val obavezno = Azuriranja.obavezno(n)
    var napredak by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var poruka by remember { mutableStateOf<String?>(null) }

    fun instaliraj(f: File) {
        if (Azuriranja.smijeInstalirati(ctx)) {
            poruka = null
            Azuriranja.instaliraj(ctx, f)
        } else {
            poruka = "Dopustite SmartBudgetu instaliranje aplikacija, vratite se i dodirnite \"Instaliraj\"."
            Azuriranja.otvoriDopustenje(ctx)
        }
    }

    fun preuzmi() {
        apk?.let { instaliraj(it); return }
        napredak = 0f
        poruka = null
        opseg.launch {
            runCatching { Azuriranja.preuzmi(ctx, n) { napredak = it } }
                .onSuccess { apk = it; napredak = null; instaliraj(it) }
                .onFailure { napredak = null; poruka = "Preuzimanje nije uspjelo: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!obavezno && napredak == null) onKasnije() },
        properties = DialogProperties(dismissOnBackPress = !obavezno, dismissOnClickOutside = !obavezno),
        containerColor = Neon.Sheet,
        icon = { PlocicaIkone(Ikone.ui("download"), if (obavezno) Neon.Crvena else Neon.Zelena, 48.dp, sjaj = true) },
        title = {
            Text(
                if (obavezno) "Obavezno ažuriranje" else "Nova verzija ${n.naziv}",
                color = Neon.Tekst, fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Instalirano: ${BuildConfig.VERSION_NAME} → novo: ${n.naziv}" +
                        if (n.velicina > 0) " · ${"%.1f".format(n.velicina / 1_048_576.0).replace('.', ',')} MB" else "",
                    style = Tip.oznaka,
                )
                if (obavezno) {
                    Text(
                        "Ova verzija donosi veće izmjene i potrebna je za nastavak rada. Podaci ostaju sačuvani.",
                        color = Neon.Crvena, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Text("Izmjene", style = Tip.naslovKartice.copy(fontSize = 15.sp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        n.izmjene.ifBlank { "Popis izmjena nije naveden." },
                        color = Neon.TekstSvijetliSiv, fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
                napredak?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Neon.Zelena, trackColor = Neon.Ispuna)
                        Text("Preuzimanje… ${(it * 100).toInt()} %", style = Tip.oznaka)
                    }
                }
                poruka?.let { Text(it, color = Neon.Jantar, fontSize = 13.sp, lineHeight = 18.sp) }
            }
        },
        confirmButton = {
            TextButton(::preuzmi, enabled = napredak == null) {
                Text(if (apk != null) "Instaliraj" else "Preuzmi i instaliraj", color = Neon.Zelena, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (obavezno) null else {
            { TextButton(onKasnije, enabled = napredak == null) { Text("Kasnije", color = Neon.TekstSiv) } }
        },
    )
}
