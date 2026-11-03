package hr.smartbudget.app.ui.screens

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

/** Preporučeni mjesečni iznos do roka (null bez roka ili kad je cilj dosegnut). */
fun mjesecnoDoCilja(c: CiljStednje, danas: LocalDate = LocalDate.now()): Long? {
    val ostalo = c.ciljCenti - c.ustedjenoCenti
    val rok = c.rokEpochDan?.let { LocalDate.ofEpochDay(it) } ?: return null
    if (ostalo <= 0) return null
    val mjeseci = ChronoUnit.MONTHS.between(danas.withDayOfMonth(1), rok.withDayOfMonth(1)).coerceAtLeast(1)
    return (ostalo + mjeseci - 1) / mjeseci
}

@Composable
fun CiljeviKartica(ciljevi: List<CiljStednje>, onNovi: () -> Unit, onUredi: (CiljStednje) -> Unit) {
    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ciljevi štednje", style = Tip.naslovKartice)
                Text("Npr. ljetovanje, novi mobitel, rezerva", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            TonalniGumb("Novi", onNovi, ikona = "plus", boja = Neon.Zelena)
        }
        if (ciljevi.isEmpty()) Text("Postavite cilj i pratite koliko ste uštedjeli i koliko treba odvajati mjesečno.", style = Tip.malo)
        ciljevi.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = Neon.RubSlab)
            val boja = hexBoja(c.bojaHex)
            val udio = if (c.ciljCenti > 0) c.ustedjenoCenti.toFloat() / c.ciljCenti else 0f
            Column(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi cilj") { onUredi(c) }.padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlocicaIkone(Ikone.ikona(c.ikonaNaziv), boja, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.naziv, style = Tip.tijelo)
                        Text("${Novac.eur(c.ustedjenoCenti)} od ${Novac.eur(c.ciljCenti)}", style = Tip.malo)
                    }
                    Text("${(udio * 100).toInt()} %", color = boja, style = Tip.iznos.copy(fontSize = 17.sp))
                }
                TrakaNapretka(udio, Brush.horizontalGradient(listOf(boja, boja.copy(alpha = .7f))), boja)
                val ostalo = c.ciljCenti - c.ustedjenoCenti
                Text(
                    when {
                        ostalo <= 0 -> "Cilj je dosegnut ✓"
                        c.rokEpochDan != null -> "Još ${Novac.eur(ostalo)} do ${HrDatum.puni(LocalDate.ofEpochDay(c.rokEpochDan))}" +
                            (mjesecnoDoCilja(c)?.let { " · ~${Novac.eur(it)} mjesečno" } ?: "")
                        else -> "Još ${Novac.eur(ostalo)}"
                    },
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
                    color = if (ostalo <= 0) Neon.Zelena else Neon.TekstSiv,
                )
            }
        }
    }
}

private val ikoneCilja = listOf("piggy", "plane", "home", "car", "gift", "phone", "heart", "star")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CiljSheet(postojeci: CiljStednje?, onSpremi: (CiljStednje) -> Unit, onObrisi: (CiljStednje) -> Unit, onZatvori: () -> Unit) {
    var naziv by remember { mutableStateOf(postojeci?.naziv ?: "") }
    var cilj by remember { mutableStateOf(postojeci?.ciljCenti?.let { Novac.broj(it) } ?: "") }
    var ustedjeno by remember { mutableStateOf(postojeci?.ustedjenoCenti?.let { if (it == 0L) "" else Novac.broj(it) } ?: "") }
    var uplata by remember { mutableStateOf("") }
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
            OznakaPolja("Ciljni iznos (€)")
            NeonPolje(
                cilj, { cilj = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true,
                stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena, fontFeatureSettings = "tnum"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Već ušteđeno (€)")
            NeonPolje(ustedjeno, { ustedjeno = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
            if (postojeci != null) {
                // Brza uplata: dodaje se na ušteđeno
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonPolje(uplata, { uplata = it }, Modifier.weight(1f), placeholder = "Uplata 0,00", uCentima = true, visina = 48.dp)
                    TonalniGumb("Dodaj", {
                        val u = Novac.parsiraj(uplata) ?: 0L
                        if (u > 0) {
                            ustedjeno = Novac.broj((Novac.parsiraj(ustedjeno) ?: 0L) + u)
                            uplata = ""
                        }
                    }, ikona = "plus", visina = 48.dp)
                }
            }
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
                            bojaHex = boja, ikonaNaziv = ikona,
                        ),
                    )
                    onZatvori()
                }
            }
        }, ikona = "check")
        if (postojeci != null) GumbBrisanja("Obriši cilj") { potvrda = true }
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
