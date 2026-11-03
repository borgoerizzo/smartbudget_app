package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.data.TransakcijaSKategorijom
import hr.smartbudget.app.domain.Ciklus
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.datum
import hr.smartbudget.app.domain.jeStvarna
import hr.smartbudget.app.domain.raspodjelaPoKategoriji
import hr.smartbudget.app.ui.KruzniGraf
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.PopisStavkiKategorije
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.ui.theme.hexBoja

/**
 * Pregled prihoda ili rashoda ciklusa: kružni graf udjela kategorija, legenda
 * i popis transakcija (po iznosu, od najveće, ili po datumu nastanka).
 */
@Composable
fun PregledSheet(
    tip: TipTransakcije,
    ciklus: Ciklus,
    sveTransakcije: List<TransakcijaSKategorijom>,
    onTransakcija: (TransakcijaSKategorijom) -> Unit,
    onZatvori: () -> Unit,
) {
    val prihodi = tip == TipTransakcije.PRIHOD
    val lista = sveTransakcije.filter { it.jeStvarna && it.transakcija.tip == tip }
    val ukupno = lista.sumOf { it.transakcija.iznosCenti }
    val udjeli = raspodjelaPoKategoriji(sveTransakcije, tip)
    // 0 = po iznosu (od najveće), 1 = po datumu (najnovije prvo)
    var poredak by rememberSaveable { mutableIntStateOf(0) }
    // Dodir na kategoriju u legendi širi popis njezinih stavki.
    var rasirene by remember { mutableStateOf(setOf<Long>()) }
    val poredane = if (poredak == 0) {
        lista.sortedWith(compareByDescending<TransakcijaSKategorijom> { it.transakcija.iznosCenti }.thenByDescending { it.transakcija.datumVrijeme })
    } else {
        lista.sortedByDescending { it.transakcija.datumVrijeme }
    }

    NeonSheet(onZatvori) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZaglavljeSheeta(if (prihodi) "Prihodi" else "Rashodi", onZatvori)
            Text(
                "Ciklus ${HrDatum.raspon(ciklus)} · ${lista.size} ${transakcijaRijec(lista.size)}",
                style = Tip.malo,
            )
        }

        if (lista.isEmpty()) {
            Text(if (prihodi) "U ovom ciklusu nema prihoda." else "U ovom ciklusu nema rashoda.", style = Tip.malo)
            return@NeonSheet
        }

        KruzniGraf(
            udjeli, if (prihodi) "Ukupno prihoda" else "Ukupno rashoda", ukupno,
            Modifier.align(Alignment.CenterHorizontally),
        )

        // Legenda: udio svake kategorije u ukupnom iznosu
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Neon.Ispuna)
                .border(1.dp, Neon.RubSlab, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            udjeli.forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                val id = u.kategorija.id
                Row(
                    Modifier
                        .clickable(onClickLabel = "Prikaži stavke") { rasirene = if (id in rasirene) rasirene - id else rasirene + id }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(hexBoja(u.kategorija.bojaHex)))
                    Text(u.kategorija.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text(Novac.postotak(u.udio), style = Tip.oznaka, textAlign = TextAlign.End, modifier = Modifier.width(64.dp).padding(end = 10.dp))
                    Text(Novac.eur(u.centi), style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                    Icon(Ikone.ui(if (id in rasirene) "chevUp" else "chevDown"), null, Modifier.padding(start = 6.dp).size(16.dp), tint = Neon.TekstSiv)
                }
                if (id in rasirene) PopisStavkiKategorije(lista.filter { it.kategorija.id == id })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transakcije", style = Tip.naslovKartice, modifier = Modifier.weight(1f))
        }
        Segmenti(listOf("Po iznosu", "Po datumu"), poredak, { poredak = it })

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Neon.Kartica)
                .border(1.dp, Neon.RubSlab, RoundedCornerShape(20.dp)),
        ) {
            var zadnjiDatum: java.time.LocalDate? = null
            poredane.forEachIndexed { i, t ->
                // Kod poretka po datumu: naslov dana iznad prve stavke tog dana.
                if (poredak == 1 && t.datum() != zadnjiDatum) {
                    zadnjiDatum = t.datum()
                    if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                    Text(
                        HrDatum.sDanom(t.datum()), style = Tip.oznaka, color = Neon.TekstSvijetliSiv,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                } else if (i > 0) {
                    HorizontalDivider(color = Neon.RubSlab)
                }
                StavkaTransakcije(t, udio = Novac.postotak(t.transakcija.iznosCenti * 100.0 / ukupno) + " ukupnog", sDatumom = poredak == 0) { onTransakcija(t) }
            }
        }
    }
}

private fun transakcijaRijec(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "transakcija"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "transakcije"
    else -> "transakcija"
}
