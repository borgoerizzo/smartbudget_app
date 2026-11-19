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
            OznakaPolja("Početno stanje (${Novac.simbol})")
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
fun PrijenosSheet(
    racuni: List<Racun>,
    onSpremi: (String, String, Long, Long, String) -> Unit,
    onZatvori: () -> Unit,
    ciljevi: List<hr.smartbudget.app.data.CiljStednje> = emptyList(),
    novcanici: List<hr.smartbudget.app.domain.StanjeNovcanika> = emptyList(),
    t: hr.smartbudget.app.ui.StanjeTecajeva = hr.smartbudget.app.ui.StanjeTecajeva(),
    onOsvjezi: () -> Unit = {},
    /** Prijenos na štednju: (s računa, iznos, vrijeme, opis, cilj ili novčanik, iznos u valuti novčanika). */
    onNaStednju: (String, Long, Long, String, hr.smartbudget.app.data.CiljStednje?, hr.smartbudget.app.data.Novcanik?, Long) -> Unit = { _, _, _, _, _, _, _ -> },
    /** Prijenos sa štednje na račun: (na račun, iznos, vrijeme, opis, cilj ili novčanik, iznos skinut s novčanika u njegovoj valuti). */
    onSaStednje: (String, Long, Long, String, hr.smartbudget.app.data.CiljStednje?, hr.smartbudget.app.data.Novcanik?, Long) -> Unit = { _, _, _, _, _, _, _ -> },
) {
    // Izvor na štednji (umjesto računa)
    var sCilja by remember { mutableStateOf<hr.smartbudget.app.data.CiljStednje?>(null) }
    var sNovcanika by remember { mutableStateOf<hr.smartbudget.app.domain.StanjeNovcanika?>(null) }
    var skida by remember { mutableStateOf("") }
    var skidaRucno by remember { mutableStateOf(false) }
    var s by remember { mutableStateOf(racuni.getOrNull(0)?.naziv) }
    var na by remember { mutableStateOf(racuni.getOrNull(1)?.naziv) }
    // Odredište na štednji (umjesto računa)
    var naCilj by remember { mutableStateOf<hr.smartbudget.app.data.CiljStednje?>(null) }
    var naNovcanik by remember { mutableStateOf<hr.smartbudget.app.domain.StanjeNovcanika?>(null) }
    var primljeno by remember { mutableStateOf("") }
    var primljenoRucno by remember { mutableStateOf(false) }
    val osnovna = osnovnaValuta()
    val stranaValuta = naNovcanik?.valuta?.takeIf { it.kod != osnovna.kod }
    val izvorValuta = sNovcanika?.valuta?.takeIf { it.kod != osnovna.kod }
    androidx.compose.runtime.LaunchedEffect(stranaValuta?.kod, izvorValuta?.kod) { if (stranaValuta != null || izvorValuta != null) onOsvjezi() }
    val saStednje = sCilja != null || sNovcanika != null
    var iznos by remember { mutableStateOf("") }
    var opis by remember { mutableStateOf("") }
    var trenutak by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    var biramDatum by remember { mutableStateOf(false) }
    var greska by remember { mutableStateOf<String?>(null) }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(if (ciljevi.isEmpty() && novcanici.isEmpty()) "Prijenos između računa" else "Prijenos", onZatvori)
        Text("Prijenos mijenja stanja računa i štednje, ali se ne broji kao prihod ni trošak.", style = Tip.malo)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OznakaPolja("Iznos (${Novac.simbol})")
            NeonPolje(
                iznos, { iznos = it; greska = null; if (!primljenoRucno) primljeno = ""; if (!skidaRucno) skida = "" }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true, visina = 64.dp,
                stil = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Cijan, fontFeatureSettings = "tnum"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("S računa")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                racuni.forEach { r ->
                    NeonChip(r.naziv, s == r.naziv && !saStednje, { s = r.naziv; sCilja = null; sNovcanika = null; greska = null }, boja = Neon.Crvena)
                }
            }
        }
        // Povlačenje sa štednje natrag na račun: vraća novac u stanje ciklusa.
        val ciljeviSNovcem = ciljevi.filter { it.ustedjenoCenti > 0 }
        val novcaniciSNovcem = novcanici.filter { it.stanje > 0 }
        if (ciljeviSNovcem.isNotEmpty() || novcaniciSNovcem.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OznakaPolja("Ili sa štednje")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ciljeviSNovcem.forEach { c ->
                        NeonChip(c.naziv, sCilja?.id == c.id, {
                            sCilja = c; sNovcanika = null; naCilj = null; naNovcanik = null; greska = null
                            if (na == null) na = racuni.firstOrNull()?.naziv
                        }, boja = hexBoja(c.bojaHex))
                    }
                    novcaniciSNovcem.forEach { n ->
                        NeonChip(if (n.novcanik.naziv.equals(n.valuta.kod, true)) n.novcanik.naziv else "${n.novcanik.naziv} (${n.valuta.kod})", sNovcanika?.novcanik?.id == n.novcanik.id, {
                            sNovcanika = n; sCilja = null; naCilj = null; naNovcanik = null; skida = ""; skidaRucno = false; greska = null
                            if (na == null) na = racuni.firstOrNull()?.naziv
                        }, boja = bojaValute(n.valuta))
                    }
                }
                sCilja?.let { Text("Na cilju u ${osnovna.kod}: ${Novac.eur(it.ustedjenoCenti)}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal)) }
                sNovcanika?.let { Text("U novčaniku: ${hr.smartbudget.app.domain.Valute.iznos(it.stanje, it.valuta)}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OznakaPolja("Na račun")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                racuni.forEach { r ->
                    NeonChip(r.naziv, na == r.naziv && naCilj == null && naNovcanik == null, {
                        na = r.naziv; naCilj = null; naNovcanik = null; greska = null
                    }, boja = Neon.Zelena)
                }
            }
        }
        if (ciljevi.isNotEmpty() && !saStednje) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OznakaPolja("Na cilj štednje")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ciljevi.forEach { c ->
                        NeonChip(c.naziv, naCilj?.id == c.id, { naCilj = c; naNovcanik = null; greska = null }, boja = hexBoja(c.bojaHex))
                    }
                }
            }
        }
        if (novcanici.isNotEmpty() && !saStednje) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OznakaPolja("Na štednju u valuti")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    novcanici.forEach { n ->
                        NeonChip(if (n.novcanik.naziv.equals(n.valuta.kod, true)) n.novcanik.naziv else "${n.novcanik.naziv} (${n.valuta.kod})", naNovcanik?.novcanik?.id == n.novcanik.id, {
                            naNovcanik = n; naCilj = null; primljeno = ""; primljenoRucno = false; greska = null
                        }, boja = bojaValute(n.valuta))
                    }
                }
            }
        }
        // Kupnja strane valute ili kriptovalute: koliko stiže u novčanik (predloženo po današnjem tečaju)
        if (stranaValuta != null) {
            val c = Novac.parsiraj(iznos)
            val predlozeno = c?.let { t.pretvori(it, osnovna, stranaValuta) }
            androidx.compose.runtime.LaunchedEffect(c, predlozeno) {
                if (!primljenoRucno && predlozeno != null) {
                    primljeno = hr.smartbudget.app.domain.Valute.parsiraj(
                        java.math.BigDecimal.valueOf(predlozeno).setScale(stranaValuta.decimale, java.math.RoundingMode.HALF_DOWN).toPlainString(), stranaValuta,
                    )?.let { j -> if (stranaValuta.decimale == 2) Novac.broj(j) else hr.smartbudget.app.domain.Valute.iznosBezOznake(j, stranaValuta.decimale) } ?: ""
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OznakaPolja("Stiže u novčanik (${stranaValuta.kod})")
                NeonPolje(
                    primljeno, { primljeno = it; primljenoRucno = true; greska = null }, Modifier.fillMaxWidth(),
                    placeholder = if (stranaValuta.kripto) "0,00000000" else "0,00", uCentima = true, decimale = stranaValuta.decimale,
                )
                Text(
                    if (predlozeno != null) "Predloženo po današnjem tečaju; upišite točan iznos ako se razlikuje (npr. zbog provizije)."
                    else if (t.ucitava) "Dohvaćam tečaj…" else "Tečaj nije dostupan; upišite koliko stiže u novčanik.",
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                )
                IzvorTecajeva(t, kripto = stranaValuta.kripto, fiat = !stranaValuta.kripto || osnovna.kod != "EUR")
            }
        }
        // Prodaja strane valute ili kriptovalute: koliko se skida s novčanika za iznos koji stiže na račun
        if (izvorValuta != null) {
            val c = Novac.parsiraj(iznos)
            val predlozeno = c?.let { t.pretvori(it, osnovna, izvorValuta) }
            androidx.compose.runtime.LaunchedEffect(c, predlozeno) {
                if (!skidaRucno && predlozeno != null) {
                    skida = hr.smartbudget.app.domain.Valute.parsiraj(
                        java.math.BigDecimal.valueOf(predlozeno).setScale(izvorValuta.decimale, java.math.RoundingMode.HALF_UP).toPlainString(), izvorValuta,
                    )?.let { j -> if (izvorValuta.decimale == 2) Novac.broj(j) else hr.smartbudget.app.domain.Valute.iznosBezOznake(j, izvorValuta.decimale) } ?: ""
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OznakaPolja("Skida se s novčanika (${izvorValuta.kod})")
                NeonPolje(
                    skida, { skida = it; skidaRucno = true; greska = null }, Modifier.fillMaxWidth(),
                    placeholder = if (izvorValuta.kripto) "0,00000000" else "0,00", uCentima = true, decimale = izvorValuta.decimale,
                )
                Text(
                    if (predlozeno != null) "Predloženo po današnjem tečaju; upišite točan iznos ako se razlikuje."
                    else if (t.ucitava) "Dohvaćam tečaj…" else "Tečaj nije dostupan; upišite koliko se skida s novčanika.",
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                )
                IzvorTecajeva(t, kripto = izvorValuta.kripto, fiat = !izvorValuta.kripto || osnovna.kod != "EUR")
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
                c == null || c <= 0 -> greska = "Unesite iznos veći od 0,00 ${Novac.simbol}."
                saStednje -> {
                    val cilj = sCilja
                    val nov = sNovcanika
                    val j = when {
                        nov == null -> 0L
                        izvorValuta == null -> c
                        else -> hr.smartbudget.app.domain.Valute.parsiraj(skida, izvorValuta) ?: 0L
                    }
                    when {
                        na == null -> greska = "Odaberite račun na koji ide novac."
                        cilj != null && c > cilj.ustedjenoCenti -> greska = "Na cilju je ${Novac.eur(cilj.ustedjenoCenti)} u ${osnovna.kod}."
                        nov != null && j <= 0 -> greska = "Upišite koliko se skida s novčanika."
                        nov != null && j > nov.stanje -> greska = "U novčaniku je samo ${hr.smartbudget.app.domain.Valute.iznos(nov.stanje, nov.valuta)}."
                        else -> {
                            onSaStednje(na!!, c, trenutak.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), opis.trim(), cilj, nov?.novcanik, j)
                            onZatvori()
                        }
                    }
                }
                s != null && (naCilj != null || naNovcanik != null) -> {
                    val j = when {
                        naNovcanik == null -> 0L
                        stranaValuta == null -> c
                        else -> hr.smartbudget.app.domain.Valute.parsiraj(primljeno, stranaValuta) ?: 0L
                    }
                    if (naNovcanik != null && j <= 0) { greska = "Upišite koliko stiže u novčanik."; return@PrimarniGumb }
                    onNaStednju(s!!, c, trenutak.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), opis.trim(), naCilj, naNovcanik?.novcanik, j)
                    onZatvori()
                }
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
