package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.InfoObavijesti
import hr.smartbudget.app.data.VrstaObavijesti
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

/** Prozor nakon dodira na obavijest: o čemu se radi, osnovni podaci i brza radnja. */
@Composable
fun ObavijestInfoDijalog(info: InfoObavijesti, stanjeCenti: Long, onAkcija: () -> Unit, onZatvori: () -> Unit) {
    val (ikona, boja) = when (info.vrsta) {
        VrstaObavijesti.PONAVLJAJUCI -> "repeat" to Neon.LjubicastaSvijetla
        VrstaObavijesti.MINUS -> "warn" to Neon.Crvena
        VrstaObavijesti.PROGNOZA -> "warn" to Neon.Jantar
        VrstaObavijesti.PODSJETNIK -> "bell" to Neon.Cijan
        VrstaObavijesti.TJEDNI -> "calendar" to Neon.Zelena
    }
    val objasnjenje = when (info.vrsta) {
        VrstaObavijesti.PONAVLJAJUCI ->
            "Na dan naplate aplikacija je sama upisala ponavljajući prihod ili trošak, isto kao da ste ga unijeli ručno. " +
                "Pravila (iznos, dan, pauza) uređujete u Budžetima → Ponavljajući unosi."
        VrstaObavijesti.MINUS ->
            "Ukupno stanje svih računa palo je ispod 0 €. Obavijest stiže jednom, u trenutku prelaska iz plusa u minus. " +
                "Minus se prenosi u sljedeći ciklus i umanjuje nove prihode."
        VrstaObavijesti.PROGNOZA ->
            "Uz dosadašnji dnevni tempo potrošnje u ovoj kategoriji limit će biti prekoračen prije kraja ciklusa. " +
                "Prognoza = potrošeno ÷ proteklih dana × dana u ciklusu. Obavijest stiže jednom po kategoriji u ciklusu."
        VrstaObavijesti.TJEDNI -> "Tjedni pregled zadnjih 7 dana."
        VrstaObavijesti.PODSJETNIK ->
            "Dnevni podsjetnik stiže u odabrano vrijeme samo ako taj dan još niste ništa unijeli. " +
                "Vrijeme mijenjate u Postavkama → Obavijesti."
    }
    val akcija = when (info.vrsta) {
        VrstaObavijesti.PONAVLJAJUCI -> "Ponavljajući unosi"
        VrstaObavijesti.MINUS -> "Pogledaj transakcije"
        VrstaObavijesti.PROGNOZA -> "Otvori Budžete"
        VrstaObavijesti.PODSJETNIK -> "Dodaj unos"
        VrstaObavijesti.TJEDNI -> "Početna"
    }
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        icon = { PlocicaIkone(Ikone.ui(ikona), boja, 48.dp, sjaj = true) },
        title = { Text(info.naslov, color = Neon.Tekst, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.test) {
                    Text(
                        "Ovo je testna obavijest: podaci su primjer, ništa nije upisano.",
                        color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                // Podaci iz same obavijesti
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    info.redovi.forEach { Text(it, style = Tip.tijelo.copy(fontSize = 14.sp)) }
                    Text(info.tekst, color = boja, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(objasnjenje, color = Neon.TekstSvijetliSiv, fontSize = 14.sp, lineHeight = 20.sp)
                if (info.vrsta == VrstaObavijesti.MINUS || info.vrsta == VrstaObavijesti.PONAVLJAJUCI) {
                    Text("Trenutno stanje računa: ${Novac.stanje(stanjeCenti)}", style = Tip.oznaka, color = if (stanjeCenti < 0) Neon.Crvena else Neon.TekstSiv)
                }
            }
        },
        confirmButton = { TextButton({ onAkcija(); onZatvori() }) { Text(akcija, color = Neon.Zelena, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onZatvori) { Text("Zatvori", color = Neon.TekstSiv) } },
    )
}
