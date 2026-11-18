package hr.smartbudget.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

/** Dijelovi aplikacije koji se mogu zaključati zasebno (opseg zaključavanja u Postavkama). */
object Dijelovi {
    const val SVE = "SVE"
    const val CIKLUS = "CIKLUS"
    const val STEDNJA = "STEDNJA"

    /** Otključani dijelovi u ovoj sesiji; prazni se nakon odgode u pozadini. */
    val otkljucano = mutableStateOf(setOf<String>())

    /** Iznosi štednje otkriveni dodirom kad je uključeno skrivanje štednje; poništava se nakon odgode u pozadini. */
    val stednjaOtkrivena = mutableStateOf(false)
}

private tailrec fun Context.aktivnostDijela(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.aktivnostDijela()
    else -> null
}

/**
 * Prikazuje [sadrzaj] ili, ako je taj dio zaključan, karticu za otključavanje biometrijom ili PIN-om.
 * Zaključavanje cijele aplikacije i dalje radi preko [EkranZakljucavanja].
 */
@Composable
fun ZasticeniDio(dio: String, naziv: String, automatski: Boolean = true, sadrzaj: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val postavke = remember { AppPostavke.get(ctx) }
    val p by postavke.stanje.collectAsState()
    val otkljucano by Dijelovi.otkljucano
    val zakljucan = p.zakljucavanje && p.imaPin && p.opsegZakljucavanja == dio && dio !in otkljucano
    if (!zakljucan) { sadrzaj(); return }

    var pinDijalog by remember { mutableStateOf(false) }
    val bio = p.biometrija && Zakljucavanje.biometrijaDostupna(ctx)
    val otkljucaj = { Dijelovi.otkljucano.value = Dijelovi.otkljucano.value + dio; pinDijalog = false }
    val pokusaj = {
        val a = ctx.aktivnostDijela()
        if (bio && a != null) Zakljucavanje.biometrija(a, otkljucaj) else pinDijalog = true
    }
    LaunchedEffect(Unit) { if (bio && automatski) pokusaj() }

    Kartica(razmak = 14.dp, sjaj = Neon.Zelena) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Otključaj", onClick = pokusaj),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            PlocicaIkone(Ikone.ui("lock"), Neon.Zelena, 64.dp, sjaj = true)
            Text(
                when (naziv) {
                    "Ciklus" -> "Ciklus je zaključan"
                    "Budžeti" -> "Budžeti su zaključani"
                    else -> "$naziv je zaključana"
                },
                style = Tip.naslov, textAlign = TextAlign.Center,
            )
            Text(if (bio) "Otisak prsta, lice ili PIN" else "Unesite PIN", color = Neon.TekstSiv, fontSize = 14.sp)
            PrimarniGumb("Otključaj", onClick = pokusaj, ikona = if (bio) "fingerprint" else "lock")
            if (bio) TextButton({ pinDijalog = true }) { Text("Koristi PIN", color = Neon.TekstSiv) }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (pinDijalog) {
        var pin by remember { mutableStateOf("") }
        var greska by remember { mutableStateOf(false) }
        Dialog({ pinDijalog = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(28.dp)).background(Neon.Sheet)
                    .border(1.dp, Neon.RubSlab, RoundedCornerShape(28.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Otključaj: $naziv", style = Tip.naslovKartice)
                Text(if (greska) "Pogrešan PIN" else "Unesite PIN", color = if (greska) Neon.Crvena else Neon.TekstSiv, fontSize = 14.sp)
                TockePina(pin.length, greska)
                PinTipkovnica(
                    onZnamenka = { z ->
                        if (pin.length < 6) {
                            pin += z; greska = false
                            if (pin.length >= 4 && postavke.provjeriPin(pin)) otkljucaj()
                            else if (pin.length == 6) { greska = true; pin = "" }
                        }
                    },
                    onBrisi = { pin = pin.dropLast(1) },
                )
            }
        }
    }
}

/** Cijeli zaslon (npr. Analitika, Budžeti) zaštićen kad je zaključan taj dio; kartica je na vrhu, s razmakom kao zasloni. */
@Composable
fun ZasticeniZaslon(dio: String, naziv: String, sadrzaj: @Composable () -> Unit) {
    val p by AppPostavke.get(LocalContext.current).stanje.collectAsState()
    val otkljucano by Dijelovi.otkljucano
    if (!(p.zakljucavanje && p.imaPin && p.opsegZakljucavanja == dio && dio !in otkljucano)) { sadrzaj(); return }
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.Companion.statusBars).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(naziv, style = Tip.naslovVeliki)
        ZasticeniDio(dio, naziv) {}
    }
}

/**
 * Iznosi štednje zamućeni dok ih korisnik dodirom ne otkrije (postavka "Sakrij štednju").
 * Otkrivanje vrijedi do sljedećeg zaključavanja / odlaska u pozadinu.
 */
@Composable
fun SkriveniIznosiStednje(sadrzaj: @Composable () -> Unit) {
    val p by AppPostavke.get(LocalContext.current).stanje.collectAsState()
    val otkriveno by Dijelovi.stednjaOtkrivena
    if (!p.sakrijStednju || otkriveno) { sadrzaj(); return }
    // Bez natpisa: dodir bilo gdje otkriva, a dodir na ukupni iznos ponovno skriva ([skrivaStednju]).
    Box {
        Box(Modifier.blur(18.dp)) { sadrzaj() }
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { Dijelovi.stednjaOtkrivena.value = true },
        )
    }
}

/** Na ukupnom iznosu štednje: dodir ponovno skriva iznose (kad je uključeno skrivanje štednje). */
@Composable
fun Modifier.skrivaStednju(): Modifier {
    val p by AppPostavke.get(LocalContext.current).stanje.collectAsState()
    return if (!p.sakrijStednju) this
    else this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
    ) { Dijelovi.stednjaOtkrivena.value = false }
}
