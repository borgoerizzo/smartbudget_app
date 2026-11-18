package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.domain.Valuta
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

/** Boja oznake valute: kriptovalute ljubičaste, euro zelen, ostale cijan. */
fun bojaValute(v: Valuta): Color = when {
    v.kripto -> Neon.LjubicastaSvijetla
    v.kod == "EUR" -> Neon.Zelena
    else -> Neon.Cijan
}

/** Kvadratna pločica s oznakom valute (umjesto ikone). */
@Composable
fun PlocicaValute(v: Valuta, velicina: androidx.compose.ui.unit.Dp = 40.dp) {
    val boja = bojaValute(v)
    Box(
        Modifier.size(velicina).clip(RoundedCornerShape(12.dp)).background(boja.copy(alpha = .16f))
            .border(1.dp, boja.copy(alpha = .35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (v.simbol.length <= 2) v.simbol else v.kod.take(4),
            color = boja, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
            fontSize = if (v.simbol.length <= 2) 18.sp else if (v.kod.length > 3) 10.sp else 12.sp,
        )
    }
}

/** Gumb koji prikazuje odabranu valutu i otvara popis za odabir. */
@Composable
fun GumbValute(v: Valuta, modifier: Modifier = Modifier, valute: List<Valuta> = Valute.sve, onOdabir: (Valuta) -> Unit) {
    var otvoren by remember { mutableStateOf(false) }
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(Neon.Ispuna)
            .border(1.dp, Neon.RubSlab, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "Odaberi valutu") { otvoren = true }
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlocicaValute(v, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(v.kod, style = Tip.tijelo.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), maxLines = 1)
            Text(v.naziv, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Ikone.ui("chevDown"), null, Modifier.size(18.dp), tint = Neon.TekstSiv)
    }
    if (otvoren) BiracValuteDijalog("Odaberi valutu", valute, v.kod, null, { onOdabir(it); otvoren = false }) { otvoren = false }
}

/** Popis valuta (i kriptovaluta ako su u popisu) za odabir. */
@Composable
fun BiracValuteDijalog(
    naslov: String,
    valute: List<Valuta>,
    odabrana: String,
    napomena: String?,
    onOdabir: (Valuta) -> Unit,
    onZatvori: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        title = { Text(naslov, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                napomena?.let { Text(it, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp), modifier = Modifier.padding(bottom = 8.dp)) }
                val grupe = valute.groupBy { it.kripto }
                grupe[false]?.let { RedoviValuta(if (grupe.size > 1) "Valute (srednji tečaj HNB-a)" else null, it, odabrana, onOdabir) }
                grupe[true]?.let { RedoviValuta("Kriptovalute (CoinGecko)", it, odabrana, onOdabir) }
            }
        },
        confirmButton = { TextButton(onZatvori) { Text("Zatvori", color = Neon.TekstSiv) } },
    )
}

@Composable
private fun RedoviValuta(naslov: String?, valute: List<Valuta>, odabrana: String, onOdabir: (Valuta) -> Unit) {
    naslov?.let { Text(it, style = Tip.oznaka, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
    valute.forEach { v ->
        val on = v.kod == odabrana
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (on) bojaValute(v).copy(alpha = .12f) else Color.Transparent)
                .clickable(role = Role.RadioButton) { onOdabir(v) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlocicaValute(v, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(v.naziv, style = Tip.tijelo.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(v.kod, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            if (on) Icon(Ikone.ui("check"), null, Modifier.size(18.dp), tint = bojaValute(v))
        }
    }
}
