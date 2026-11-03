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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.NeonChip
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.StanjeRacunaUi
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja
import hr.smartbudget.app.ui.theme.svjetlija
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Vodoravna traka računa: stanje svakog računa, novi račun i prijenos između računa. */
@Composable
fun RacuniTraka(
    stanja: List<StanjeRacunaUi>,
    sakrijStanje: Boolean,
    onRacun: (Racun) -> Unit,
    onNovi: () -> Unit,
    onPrijenos: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Računi", style = Tip.naslovKartice, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (stanja.size >= 2) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Prijenos između računa", onClick = onPrijenos)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ikone.ui("transfer"), null, Modifier.size(16.dp), tint = Neon.Cijan)
                    Text(" Prijenos", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stanja, key = { it.racun.id }) { s ->
                val boja = hexBoja(s.racun.bojaHex)
                Column(
                    Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Neon.Kartica)
                        .border(1.dp, boja.copy(alpha = .35f), RoundedCornerShape(18.dp))
                        .clickable(onClickLabel = "Uredi račun") { onRacun(s.racun) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ikone.ikona(s.racun.ikonaNaziv), null, Modifier.size(16.dp), tint = boja.svjetlija())
                        Text(" ${s.racun.naziv}", style = Tip.oznaka, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        if (sakrijStanje) AppPostavke.SKRIVENO else Novac.stanje(s.stanje),
                        style = Tip.iznos.copy(fontSize = 16.sp),
                        color = if (s.stanje < 0) Neon.Crvena else Neon.Tekst,
                        maxLines = 1,
                    )
                }
            }
            item {
                Box(
                    Modifier
                        .width(64.dp)
                        .height(66.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Neon.Rub, RoundedCornerShape(18.dp))
                        .clickable(onClickLabel = "Novi račun", onClick = onNovi),
                    contentAlignment = Alignment.Center,
                ) { Icon(Ikone.ui("plus"), "Novi račun", Modifier.size(22.dp), tint = Neon.Zelena) }
            }
        }
    }
}

private val ikoneRacuna = listOf("bank", "card", "wallet", "piggy", "phone", "brief")

/** Novi ili postojeći račun: naziv, početno stanje (može biti u minusu), boja, ikona. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RacunSheet(postojeci: Racun?, onSpremi: (Racun, String?) -> Unit, onObrisi: (Racun) -> Unit, onZatvori: () -> Unit) {
    var naziv by remember { mutableStateOf(postojeci?.naziv ?: "") }
    var iznos by remember { mutableStateOf(postojeci?.pocetnoStanjeCenti?.let { if (it == 0L) "" else Novac.broj(kotlin.math.abs(it)) } ?: "") }
    var minus by remember { mutableStateOf((postojeci?.pocetnoStanjeCenti ?: 0) < 0) }
    var boja by remember { mutableStateOf(postojeci?.bojaHex ?: "#4D8BFF") }
    var ikona by remember { mutableStateOf(postojeci?.ikonaNaziv ?: "bank") }
    var greska by remember { mutableStateOf<String?>(null) }
    var potvrda by remember { mutableStateOf(false) }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(if (postojeci == null) "Novi račun" else "Uredi račun", onZatvori)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Naziv računa")
            NeonPolje(naziv, { naziv = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "npr. Štednja, Revolut")
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Početno stanje (€)")
            Text("Stanje računa prije prvog unosa u aplikaciji", style = Tip.malo.copy(fontSize = 12.sp))
            NeonPolje(
                iznos, { iznos = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true,
                stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (minus) Neon.Crvena else Neon.Tekst, fontFeatureSettings = "tnum"),
            )
            Segmenti(listOf("U plusu", "U minusu"), if (minus) 1 else 0, { minus = it == 1 }, boje = listOf(Neon.Zelena, Neon.Crvena))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Ikona i boja")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ikoneRacuna.forEach { n ->
                    val on = n == ikona
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) hexBoja(boja).copy(alpha = .22f) else Color(0x0AFFFFFF))
                            .border(1.dp, if (on) hexBoja(boja) else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { ikona = n },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ikone.ikona(n), null, Modifier.size(22.dp), tint = if (on) hexBoja(boja).svjetlija() else Neon.TekstSrednji) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Neon.paleta.forEach { hex ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(hexBoja(hex))
                            .border(2.dp, if (hex == boja) Neon.Tekst else Color.Transparent, RoundedCornerShape(15.dp))
                            .clickable(role = Role.RadioButton) { boja = hex },
                    )
                }
            }
        }
        greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
        PrimarniGumb("Spremi račun", onClick = {
            if (naziv.isBlank()) { greska = "Unesite naziv računa."; return@PrimarniGumb }
            val c = (Novac.parsiraj(iznos) ?: 0L) * (if (minus) -1 else 1)
            onSpremi(
                Racun(id = postojeci?.id ?: 0, naziv = naziv.trim(), bojaHex = boja, ikonaNaziv = ikona, pocetnoStanjeCenti = c, redoslijed = postojeci?.redoslijed ?: 50),
                postojeci?.naziv,
            )
            onZatvori()
        }, ikona = "check")
        if (postojeci != null) GumbBrisanja("Obriši račun") { potvrda = true }
    }
    if (potvrda && postojeci != null) {
        PotvrdaBrisanja("Obrisati račun?", "Račun se može obrisati samo ako nema transakcija.", { potvrda = false }) {
            onObrisi(postojeci); potvrda = false; onZatvori()
        }
    }
}

/** Prijenos između dva računa (ne ulazi u prihode/troškove). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrijenosSheet(racuni: List<Racun>, onSpremi: (String, String, Long, Long, String) -> Unit, onZatvori: () -> Unit) {
    var s by remember { mutableStateOf(racuni.getOrNull(0)?.naziv) }
    var na by remember { mutableStateOf(racuni.getOrNull(1)?.naziv) }
    var iznos by remember { mutableStateOf("") }
    var opis by remember { mutableStateOf("") }
    var trenutak by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    var biramDatum by remember { mutableStateOf(false) }
    var greska by remember { mutableStateOf<String?>(null) }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta("Prijenos između računa", onZatvori)
        Text("Prijenos mijenja stanja računa, ali se ne broji kao prihod ni trošak.", style = Tip.malo)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Iznos (€)")
            NeonPolje(
                iznos, { iznos = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true, visina = 64.dp,
                stil = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Cijan, fontFeatureSettings = "tnum"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("S računa")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                racuni.forEach { r -> NeonChip(r.naziv, s == r.naziv, { s = r.naziv; greska = null }, boja = Neon.Crvena) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Na račun")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                racuni.forEach { r -> NeonChip(r.naziv, na == r.naziv, { na = r.naziv; greska = null }, boja = Neon.Zelena) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Napomena (opcionalno)")
            NeonPolje(opis, { opis = it }, Modifier.fillMaxWidth(), placeholder = "npr. Podizanje gotovine")
        }
        GumbPolja("calendar", HrDatum.puni(trenutak.toLocalDate()), Modifier.fillMaxWidth()) { biramDatum = true }
        greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp, textAlign = TextAlign.Start) }
        PrimarniGumb("Prenesi", onClick = {
            val c = Novac.parsiraj(iznos)
            when {
                c == null || c <= 0 -> greska = "Unesite iznos veći od 0,00 €."
                s == null || na == null -> greska = "Odaberite oba računa."
                s == na -> greska = "Odaberite dva različita računa."
                else -> {
                    onSpremi(s!!, na!!, c, trenutak.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), opis.trim())
                    onZatvori()
                }
            }
        }, ikona = "check")
    }
    if (biramDatum) {
        val st = rememberDatePickerState(initialSelectedDateMillis = trenutak.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { biramDatum = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms -> trenutak = LocalDateTime.of(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate(), trenutak.toLocalTime()) }
                    biramDatum = false
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramDatum = false }) { Text("Odustani", color = Neon.TekstSiv) } },
            colors = DatePickerDefaults.colors(containerColor = Neon.Sheet),
        ) { DatePicker(st, colors = DatePickerDefaults.colors(containerColor = Neon.Sheet, selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu)) }
    }
}
