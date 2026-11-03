package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.PonavljajuciSKategorijom
import hr.smartbudget.app.data.PonavljajuciUnos
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.domain.Ponavljanje
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.StatusLimita
import hr.smartbudget.app.ui.BudzetKategorije
import hr.smartbudget.app.ui.BudzetiStanje
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PlocicaKategorije
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.neonSjaj
import hr.smartbudget.app.ui.stilStatusa
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import kotlin.math.roundToInt

@Composable
fun BudzetiEkran(
    s: BudzetiStanje,
    ponavljajuci: List<PonavljajuciSKategorijom>,
    onNova: () -> Unit,
    onUredi: (Kategorija) -> Unit,
    onNoviPonavljajuci: () -> Unit,
    onUrediPonavljajuci: (PonavljajuciUnos) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kategorije i budžeti", style = Tip.naslovVeliki.copy(fontSize = 26.sp))
                    Text("Ciklus: ${HrDatum.raspon(s.ciklus)}", style = Tip.malo.copy(fontSize = 14.sp))
                }
                TonalniGumb("Nova", onNova, ikona = "plus", modifier = Modifier.neonSjaj(Neon.Zelena, 14.dp, 22.dp, .22f))
            }
        }
        item { UkupniLimit(s) }
        item { PonavljajuciKartica(ponavljajuci, onNoviPonavljajuci, onUrediPonavljajuci) }
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Limiti po kategorijama", style = Tip.naslovKartice.copy(fontSize = 18.sp))
                Text(
                    "Poredano po iskorištenosti. Sigurno do 80 %, blizu limita 80–99 %, prekoračeno od 100 %. Dodirnite karticu za uređivanje.",
                    style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
                )
            }
        }
        items(s.troskovne, key = { it.kategorija.id }) { b -> KarticaKategorije(b) { onUredi(b.kategorija) } }
        if (s.prihodovne.isNotEmpty()) {
            item { Text("Kategorije prihoda", style = Tip.naslovKartice.copy(fontSize = 18.sp), modifier = Modifier.padding(start = 4.dp, top = 10.dp)) }
            items(s.prihodovne, key = { it.kategorija.id }) { b -> KarticaPrihoda(b) { onUredi(b.kategorija) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UkupniLimit(s: BudzetiStanje) {
    val posto = if (s.ukupniLimit > 0) s.potrosenoULimitu * 100.0 / s.ukupniLimit else 0.0
    val status = when {
        s.ukupniLimit == 0L -> StatusLimita.BEZ_LIMITA
        posto >= 100 -> StatusLimita.PREKORACENO
        posto >= 80 -> StatusLimita.BLIZU
        else -> StatusLimita.SIGURNO
    }
    val st = stilStatusa(status)
    Kartica(pozadina = Neon.KarticaSvijetla, rub = Neon.Rub, sjaj = if (status == StatusLimita.BEZ_LIMITA) null else st.boja, padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Ukupni limit ciklusa", style = Tip.oznaka.copy(fontSize = 13.sp))
            if (s.ukupniLimit > 0) Oznaka("${posto.roundToInt()} % iskorišteno", st.boja)
        }
        if (s.ukupniLimit == 0L) {
            Text("Još niste postavili limite. Dodirnite kategoriju i upišite limit po ciklusu.", style = Tip.malo)
            return@Kartica
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst)) { append(Novac.eur(s.potrosenoULimitu)) }
                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Neon.TekstSiv)) { append("  od ${Novac.eur(s.ukupniLimit)}") }
            },
            style = Tip.iznos,
        )
        TrakaNapretka((posto / 100).toFloat(), st.ispuna, st.boja, visina = 12.dp, marker = .8f)
        val ostatak = s.ukupniLimit - s.potrosenoULimitu
        Text(
            buildAnnotatedString {
                append(if (ostatak >= 0) "Preostalo " else "Prekoračeno za ")
                withStyle(SpanStyle(color = Neon.Tekst, fontWeight = FontWeight.Bold)) { append(Novac.eur(kotlin.math.abs(ostatak))) }
                append(if (ostatak >= 0) " do kraja ciklusa" else "")
            },
            color = Neon.TekstSvijetliSiv, fontSize = 13.sp,
        )
        val n = s.troskovne.filter { it.posto != null }.groupingBy { it.status }.eachCount()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(StatusLimita.SIGURNO to "sigurno", StatusLimita.BLIZU to "blizu limita", StatusLimita.PREKORACENO to "prekoračeno").forEach { (stt, txt) ->
                Oznaka("${n[stt] ?: 0} $txt", stilStatusa(stt).boja)
            }
        }
    }
}

@Composable
private fun KarticaKategorije(b: BudzetKategorije, onClick: () -> Unit) {
    val st = stilStatusa(b.status)
    val lim = b.kategorija.mjesecniLimitCenti
    Kartica(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi kategoriju", onClick = onClick),
        zaobljenje = 22.dp,
        rub = st.rub,
        sjaj = if (b.status == StatusLimita.PREKORACENO) Neon.CrvenaJaka else null,
        padding = 14.dp,
        razmak = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlocicaKategorije(b.kategorija)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(b.kategorija.naziv, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold))
                Text(
                    if (lim != null) "${Novac.eur(b.potroseno)} od ${Novac.eur(lim)}" else "Bez limita · ${Novac.eur(b.potroseno)} potrošeno",
                    style = Tip.malo,
                )
            }
            if (b.posto != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${b.posto.roundToInt()} %", color = st.boja, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, style = Tip.iznos)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ikone.ui(st.ikona), null, Modifier.size(12.dp), tint = st.boja)
                        Text(" ${st.naziv}", color = st.boja, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Text("Postavi limit", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (b.posto != null && lim != null) {
            TrakaNapretka((b.posto / 100).toFloat(), st.ispuna, st.boja, marker = .8f)
            val ostatak = lim - b.potroseno
            Text(
                if (ostatak >= 0) "Preostalo ${Novac.eur(ostatak)}" else "Prekoračeno za ${Novac.eur(-ostatak)}",
                color = Neon.TekstSrednji, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PonavljajuciKartica(
    lista: List<PonavljajuciSKategorijom>,
    onNovi: () -> Unit,
    onUredi: (PonavljajuciUnos) -> Unit,
) {
    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ponavljajući unosi", style = Tip.naslovKartice)
                Text("Isti prihod ili trošak svaki mjesec", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            TonalniGumb("Novi", onNovi, ikona = "plus", boja = Neon.LjubicastaTekst)
        }
        if (lista.isEmpty()) {
            Text("Npr. plaća 10. u mjesecu ili najam 1. u mjesecu: unos se stvara sam na taj datum.", style = Tip.malo)
        }
        lista.forEachIndexed { i, r ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = Neon.RubSlab)
            val p = r.unos
            val prihod = p.tip == TipTransakcije.PRIHOD
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi ponavljajući unos") { onUredi(p) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlocicaKategorije(r.kategorija, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.opis.ifBlank { r.kategorija.naziv }, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1)
                    Text(
                        if (p.aktivan) "Svakog ${p.danUMjesecu}. · sljedeći ${HrDatum.kratko(Ponavljanje.sljedeciDatum(p))}" else "Pauzirano",
                        style = Tip.oznaka.copy(fontWeight = FontWeight.Normal),
                    )
                }
                Text(
                    Novac.predznak(if (prihod) p.iznosCenti else -p.iznosCenti),
                    style = Tip.iznos.copy(fontSize = 14.sp),
                    color = when { !p.aktivan -> Neon.TekstSiv; prihod -> Neon.Zelena; else -> Neon.Crvena },
                )
            }
        }
    }
}

@Composable
private fun KarticaPrihoda(b: BudzetKategorije, onClick: () -> Unit) {
    Kartica(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Uredi kategoriju", onClick = onClick),
        zaobljenje = 22.dp, padding = 14.dp, razmak = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlocicaKategorije(b.kategorija)
            Text(b.kategorija.naziv, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(Novac.predznak(b.potroseno), color = if (b.potroseno > 0) Neon.Zelena else Neon.TekstSiv, style = Tip.iznos)
        }
    }
}
