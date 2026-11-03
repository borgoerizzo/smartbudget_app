package hr.smartbudget.app.ui.screens

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
import androidx.compose.runtime.Composable
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
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
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
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Zaglavlje(s, onPrethodni, onSljedeci, onTrenutni, onUrediCiklus) }
            item { HeroKartica(s) }
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
                        naslov = if (s.filter == FilterTransakcija.SVE) "Još nema transakcija" else "Nema transakcija za ovaj filter",
                        tekst = "Dodajte prvi prihod ili trošak u ovom ciklusu gumbom +.",
                        akcija = "Dodaj transakciju",
                        onAkcija = onDodaj,
                    )
                }
            }
            items(s.grupe, key = { it.datum.toEpochDay() }) { g -> GrupaDanaKartica(g, onTransakcija) }
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

@Composable
private fun Zaglavlje(s: PocetnaStanje, onPrethodni: () -> Unit, onSljedeci: () -> Unit, onTrenutni: () -> Unit, onUredi: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (s.jeAktivni) "AKTIVNI OBRAČUNSKI CIKLUS" else "OBRAČUNSKI CIKLUS · dodirni za trenutni",
                color = Neon.TekstSiv, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            )
            BiracCiklusa(HrDatum.raspon(s.ciklus), onPrethodni, onSljedeci, onTrenutni)
        }
        KruzniGumb("edit", "Uredi dan početka ciklusa", onUredi, velicina = 48.dp)
    }
}

@Composable
private fun HeroKartica(s: PocetnaStanje) {
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
        val iznos = Novac.stanje(neto).removeSuffix(" €")
        Text(
            buildAnnotatedString {
                append(iznos)
                withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)) { append(" €") }
            },
            style = TextStyle(
                fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = netoBoja, letterSpacing = (-0.9).sp,
                fontFeatureSettings = "tnum", shadow = Shadow(netoBoja.copy(alpha = .45f), blurRadius = 28f),
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubMetrika("Prihodi", Novac.predznak(s.zbroj.prihodi), Neon.Zelena, "arrowDownLeft", Modifier.weight(1f))
            SubMetrika("Rashodi", Novac.predznak(-s.zbroj.troskovi), Neon.Crvena, "arrowUpRight", Modifier.weight(1f))
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
            MalaMetrika("Stanje računa", Novac.stanje(s.ukupnoStanje), if (s.ukupnoStanje >= 0) Neon.Tekst else Neon.Crvena, Modifier.weight(1f))
            MalaMetrika("Dnevni prosjek", Novac.eur(s.dnevniProsjek) + "/dan", Neon.Tekst, Modifier.weight(1f))
        }
        s.tempo?.let { (tekst, upozorenje) ->
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
private fun SubMetrika(naslov: String, iznos: String, boja: Color, ikona: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Neon.Ispuna)
            .border(1.dp, Neon.RubSlab, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(boja.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Ikone.ui(ikona), null, Modifier.size(16.dp), tint = boja)
            }
            Text(naslov, style = Tip.oznaka)
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
private fun GrupaDanaKartica(g: GrupaDana, onTransakcija: (TransakcijaSKategorijom) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(g.naslov, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(8.dp))
            Text(g.podnaslov, style = Tip.malo, modifier = Modifier.weight(1f))
            Text(Novac.predznak(g.neto), color = if (g.neto >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tip.iznos)
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
                StavkaTransakcije(t) { onTransakcija(t) }
            }
        }
    }
}

@Composable
fun StavkaTransakcije(t: TransakcijaSKategorijom, onClick: () -> Unit) {
    val prihod = t.transakcija.tip == TipTransakcije.PRIHOD
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
            Text(
                Novac.predznak(if (prihod) t.transakcija.iznosCenti else -t.transakcija.iznosCenti),
                style = Tip.iznos, color = if (prihod) Neon.Zelena else Neon.Crvena,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f, fill = false)) { PillKategorije(t.kategorija) }
            Spacer(Modifier.weight(1f))
            Text("${t.transakcija.nacinPlacanja} · ${HrDatum.vrijeme(t.vrijeme())}", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
    }
}
