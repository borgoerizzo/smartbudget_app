package hr.smartbudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

/** Kartica zaslona koju korisnik može premjestiti ili sakriti. */
data class StavkaRasporeda(val kljuc: String, val naziv: String, val opis: String = "")

/** Redoslijed iz postavki (nepoznati ključevi se izostavljaju, nove kartice dodaju na kraj). */
fun poredaj(zadano: List<StavkaRasporeda>, spremljeno: List<String>): List<StavkaRasporeda> {
    val po = zadano.associateBy { it.kljuc }
    val prvo = spremljeno.mapNotNull { po[it] }
    return prvo + zadano.filter { it !in prvo }
}

/**
 * Uređivanje rasporeda zaslona: prekidač prikazuje ili skriva karticu, strelice je pomiču gore/dolje,
 * "Zadano" vraća početni raspored i prikazuje sve.
 */
@Composable
fun UrediRasporedSheet(
    naslov: String,
    zadano: List<StavkaRasporeda>,
    spremljeno: List<String>,
    skrivene: Set<String>,
    onSpremi: (List<String>, Set<String>) -> Unit,
    onZadano: () -> Unit,
    onZatvori: () -> Unit,
) {
    val red = remember { mutableStateListOf<StavkaRasporeda>().apply { addAll(poredaj(zadano, spremljeno)) } }
    var sakrivene by remember { mutableStateOf(skrivene) }
    NeonSheet(onZatvori) {
        ZaglavljeSheeta(naslov, onZatvori)
        Text("Uključite ili isključite prikaz kartica i strelicama promijenite redoslijed.", style = Tip.malo)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            red.forEachIndexed { i, st ->
                val vidljiva = st.kljuc !in sakrivene
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (vidljiva) Neon.Ispuna else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, Neon.RubSlab, RoundedCornerShape(16.dp))
                        .clickable { sakrivene = if (vidljiva) sakrivene + st.kljuc else sakrivene - st.kljuc }
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        vidljiva, { sakrivene = if (it) sakrivene - st.kljuc else sakrivene + st.kljuc },
                        colors = SwitchDefaults.colors(checkedTrackColor = Neon.Zelena, checkedThumbColor = Neon.NaGumbu),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            st.naziv, style = Tip.tijelo.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            color = if (vidljiva) Neon.Tekst else Neon.TekstSiv, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (st.opis.isNotBlank()) Text(st.opis, style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp), maxLines = 1)
                    }
                    KruzniGumb("chevUp", "Pomakni gore", { if (i > 0) { val x = red.removeAt(i); red.add(i - 1, x) } }, velicina = 36.dp, boja = if (i > 0) Neon.Tekst else Neon.Neaktivno)
                    Spacer(Modifier.width(4.dp))
                    KruzniGumb("chevDown", "Pomakni dolje", { if (i < red.lastIndex) { val x = red.removeAt(i); red.add(i + 1, x) } }, velicina = 36.dp, boja = if (i < red.lastIndex) Neon.Tekst else Neon.Neaktivno)
                }
            }
        }
        PrimarniGumb("Spremi raspored", onClick = { onSpremi(red.map { it.kljuc }, sakrivene); onZatvori() }, ikona = "check")
        TonalniGumb("Zadano", { onZadano(); onZatvori() }, Modifier.fillMaxWidth(), ikona = "restore", boja = Neon.TekstSvijetliSiv, visina = 48.dp)
    }
}

/** Mali gumb za prilagodbu rasporeda u zaglavlju zaslona. */
@Composable
fun GumbRasporeda(onClick: () -> Unit) = KruzniGumb("raspored", "Prilagodi prikaz", onClick, velicina = 40.dp, boja = Neon.Cijan)
