package hr.smartbudget.app.ui.screens

import hr.smartbudget.app.domain.Novac
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.BuildConfig
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.Obavijesti
import hr.smartbudget.app.data.VrstaObavijesti
import hr.smartbudget.app.data.PonavljajuciWorker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import hr.smartbudget.app.ui.BudgetViewModel
import hr.smartbudget.app.ui.Ikone
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.NeonChip
import hr.smartbudget.app.ui.PlocicaIkone
import hr.smartbudget.app.ui.PostavljanjePinaDijalog
import hr.smartbudget.app.ui.Zakljucavanje
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import hr.smartbudget.app.widget.StanjeWidget
import kotlinx.coroutines.launch

@Composable
private fun Prekidac(ukljuceno: Boolean, omoguceno: Boolean = true, onPromjena: (Boolean) -> Unit) {
    Switch(
        ukljuceno, onPromjena, enabled = omoguceno,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Neon.Zelena, checkedThumbColor = Neon.NaGumbu,
            uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = Neon.TekstSiv, uncheckedBorderColor = Color(0x33FFFFFF),
        ),
    )
}

/** Redak postavke: ikona, naziv, opis i prekidač; dodir na cijeli redak mijenja prekidač. */
@Composable
private fun RedakPrekidac(
    ikona: String,
    boja: Color,
    naziv: String,
    opis: String,
    ukljuceno: Boolean,
    omoguceno: Boolean = true,
    /** Diskretni "Test" ispod opisa (testna obavijest). */
    onTest: (() -> Unit)? = null,
    onPromjena: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = omoguceno, role = Role.Switch) { onPromjena(!ukljuceno) }
            .padding(vertical = 6.dp),
        // Vrh ikone u ravnini s nazivom postavke; prekidač u sredini retka.
        verticalAlignment = Alignment.Top,
    ) {
        PlocicaIkone(Ikone.ui(ikona), boja, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(naziv, style = Tip.tijelo.copy(fontSize = 14.sp))
            Text(opis, style = Tip.malo)
            if (onTest != null) {
                Text(
                    "Test",
                    color = Neon.Cijan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp).clickable(role = Role.Button, onClickLabel = "Pošalji testnu obavijest", onClick = onTest)
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
        Box(Modifier.align(Alignment.CenterVertically)) { Prekidac(ukljuceno, omoguceno, onPromjena) }
    }
}

/** Zaključavanje (biometrija + PIN) te skrivanje stanja i prihoda. */
@Composable
fun SigurnostKartica() {
    val ctx = LocalContext.current
    val postavke = remember { AppPostavke.get(ctx) }
    val p by postavke.stanje.collectAsState()
    var postavljamPin by remember { mutableStateOf<Boolean?>(null) } // true = i uključi zaključavanje
    val bioDostupna = remember { Zakljucavanje.biometrijaDostupna(ctx) }
    fun osvjeziWidget() = BudgetRepository.get(ctx).opseg.launch { StanjeWidget.osvjezi(ctx) }

    Kartica(razmak = 6.dp) {
        Text("Sigurnost i privatnost", style = Tip.naslovKartice, modifier = Modifier.padding(bottom = 6.dp))
        RedakPrekidac(
            "lock", Neon.Zelena, "Zaključavanje aplikacije",
            if (!p.zakljucavanje) "Isključeno" else when (p.opsegZakljucavanja) {
                "CIKLUS" -> "Biometrija ili PIN za ciklus (Početna, Analitika, Budžeti)"
                "STEDNJA" -> "Biometrija ili PIN za štednju"
                else -> "Biometrija ili PIN pri otvaranju"
            },
            p.zakljucavanje,
        ) { ukljuci ->
            if (ukljuci && !p.imaPin) postavljamPin = true else postavke.postaviZakljucavanje(ukljuci)
        }
        if (p.zakljucavanje) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Što zaključati", style = Tip.oznaka)
                val opsezi = listOf("SVE", "CIKLUS", "STEDNJA")
                Segmenti(
                    listOf("Cijela aplikacija", "Samo ciklus", "Samo štednja"),
                    opsezi.indexOf(p.opsegZakljucavanja).coerceAtLeast(0),
                    { postavke.postaviOpsegZakljucavanja(opsezi[it]); hr.smartbudget.app.ui.Dijelovi.otkljucano.value = emptySet() },
                )
            }
            RedakPrekidac(
                "fingerprint", Neon.Cijan, "Otisak prsta ili lice",
                if (bioDostupna) "Ako ne uspije, otključava se PIN-om" else "Nije dostupno na ovom uređaju (koristi se PIN)",
                p.biometrija && bioDostupna, omoguceno = bioDostupna,
            ) { postavke.postaviBiometriju(it) }
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zaključaj nakon", style = Tip.oznaka)
                val odgode = listOf(0, 60, 300)
                Segmenti(
                    listOf("Odmah", "1 min", "5 min"),
                    odgode.indexOf(p.odgodaSekundi).coerceAtLeast(0),
                    { postavke.postaviOdgodu(odgode[it]) },
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { postavljamPin = false }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Promijeni PIN", style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Icon(Ikone.ui("chevRight"), null, Modifier.size(18.dp), tint = Neon.TekstSiv)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.RubSlab))
        RedakPrekidac(
            "eyeOff", Neon.LjubicastaSvijetla, "Sakrij stanje računa",
            "Stanje i neto na Početnoj i widgetu kao •••• (dodir otkriva)",
            p.sakrijStanje,
        ) { postavke.postaviSakrijStanje(it); osvjeziWidget() }
        RedakPrekidac(
            "eyeOff", Neon.Zelena, "Sakrij prihod",
            "Iznosi prihoda na Početnoj i widgetu kao ••••",
            p.sakrijPrihod,
        ) { postavke.postaviSakrijPrihod(it); osvjeziWidget() }
        RedakPrekidac(
            "eyeOff", Neon.Cijan, "Sakrij štednju",
            "Iznosi ciljeva, novčanika i analitike štednje skriveni (dodir otkriva)",
            p.sakrijStednju,
        ) { postavke.postaviSakrijStednju(it); hr.smartbudget.app.ui.Dijelovi.stednjaOtkrivena.value = false }
    }

    postavljamPin?.let { ukljuci ->
        PostavljanjePinaDijalog(
            onGotovo = { pin ->
                postavke.postaviPin(pin)
                if (ukljuci) postavke.postaviZakljucavanje(true)
                postavljamPin = null
            },
            onOdustani = { postavljamPin = null },
        )
    }
}

/** Obavijesti: naplaćeni ponavljajući unosi, minus, prognoza limita i dnevni podsjetnik. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObavijestiKartica(vm: BudgetViewModel) {
    val ctx = LocalContext.current
    val postavke = remember { AppPostavke.get(ctx) }
    val p by postavke.stanje.collectAsState()
    // Koje vrijeme se bira: "podsjetnik" ili "tjedni"
    var biramVrijeme by remember { mutableStateOf<String?>(null) }
    // Što uključiti nakon što korisnik dopusti obavijesti (Android 13+).
    var nakonDopustenja by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dopustenje = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { odobreno ->
        if (odobreno) nakonDopustenja?.invoke() else vm.poruka("Obavijesti nisu dopuštene. Možete ih uključiti u postavkama sustava.")
        nakonDopustenja = null
    }
    fun ukljuci(radnja: () -> Unit) {
        if (Obavijesti.dopusteno(ctx) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) radnja()
        else { nakonDopustenja = radnja; dopustenje.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Kartica(razmak = 6.dp) {
        Text("Obavijesti", style = Tip.naslovKartice, modifier = Modifier.padding(bottom = 6.dp))
        RedakPrekidac(
            "repeat", Neon.LjubicastaSvijetla, "Naplata ponavljajućeg unosa",
            "Obavijest kad se automatski upiše ponavljajući prihod ili trošak",
            p.obavijestPonavljajuci,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.PONAVLJAJUCI) } },
        ) { if (it) ukljuci { postavke.postaviObavijestPonavljajuci(true) } else postavke.postaviObavijestPonavljajuci(false) }
        RedakPrekidac(
            "warn", Neon.Crvena, "Stanje u minusu",
            "Upozorenje kad stanje računa padne ispod 0 ${Novac.simbol}",
            p.obavijestMinus,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.MINUS) } },
        ) { if (it) ukljuci { postavke.postaviObavijestMinus(true) } else postavke.postaviObavijestMinus(false) }
        RedakPrekidac(
            "warn", Neon.Jantar, "Prognoza prekoračenja limita",
            "Upozorenje unaprijed kad tempo potrošnje vodi preko limita kategorije",
            p.obavijestPrognoza,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.PROGNOZA) } },
        ) { if (it) ukljuci { postavke.postaviObavijestPrognoza(true) } else postavke.postaviObavijestPrognoza(false) }
        RedakPrekidac(
            "calendar", Neon.Jantar, "Rok ulaganja",
            "Podsjetnik 30, 15, 5 i 1 dan prije isteka roka (trezorski zapisi, obveznice, ulaganja, novčanici) i na dan isteka",
            p.obavijestRok,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.ROK) } },
        ) { if (it) ukljuci { postavke.postaviObavijestRok(true); vm.provjeriRokove() } else postavke.postaviObavijestRok(false) }
        RedakPrekidac(
            "bell", Neon.Cijan, "Dnevni podsjetnik za unos",
            if (p.podsjetnik) "Svaki dan u %02d:%02d ako taj dan nema unosa".format(p.podsjetnikMinuta / 60, p.podsjetnikMinuta % 60)
            else "Podsjetnik ako tog dana niste ništa unijeli",
            p.podsjetnik,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.PODSJETNIK) } },
        ) { ukljuceno ->
            if (ukljuceno) ukljuci { postavke.postaviPodsjetnik(true); PonavljajuciWorker.zakaziPodsjetnik(ctx) }
            else { postavke.postaviPodsjetnik(false); PonavljajuciWorker.zakaziPodsjetnik(ctx) }
        }
        if (p.podsjetnik) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { biramVrijeme = "podsjetnik" }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vrijeme podsjetnika", style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Text("%02d:%02d".format(p.podsjetnikMinuta / 60, p.podsjetnikMinuta % 60), color = Neon.Cijan, fontWeight = FontWeight.Bold)
            }
        }
        // Bez dopuštenja za točne alarme Android smije podsjetnik odgoditi do sat vremena.
        var tocno by remember { mutableStateOf(hr.smartbudget.app.data.Alarmi.mozeTocno(ctx)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            tocno = hr.smartbudget.app.data.Alarmi.mozeTocno(ctx)
            if (tocno) hr.smartbudget.app.data.Alarmi.obnovi(ctx)
            onPauseOrDispose { }
        }
        if ((p.podsjetnik || p.tjedni) && !tocno && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    ctx.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName)),
                    )
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Obavijesti u točno vrijeme", style = Tip.tijelo.copy(fontSize = 14.sp))
                    Text("Dopusti \"Alarmi i podsjetnici\", inače mogu kasniti do sat vremena", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                }
                Text("Dopusti", color = Neon.Jantar, fontWeight = FontWeight.Bold)
            }
        }
        RedakPrekidac(
            "calendar", Neon.Zelena, "Tjedni pregled",
            if (p.tjedni) "${daniPuni[p.tjedniDan - 1]} u %02d:%02d · rekapitulacija zadnjih 7 dana".format(p.tjedniMinuta / 60, p.tjedniMinuta % 60)
            else "Rekapitulacija zadnjih 7 dana s grafom, za dijeljenje kao slika",
            p.tjedni,
            onTest = { ukljuci { Obavijesti.test(ctx, VrstaObavijesti.TJEDNI) } },
        ) { ukljuceno ->
            if (ukljuceno) ukljuci { postavke.postaviTjedni(true); PonavljajuciWorker.zakaziTjedni(ctx) }
            else { postavke.postaviTjedni(false); PonavljajuciWorker.zakaziTjedni(ctx) }
        }
        if (p.tjedni) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dan u tjednu", style = Tip.oznaka)
                // Po četiri dana u retku, sve iste širine
                daniKratki.chunked(4).forEachIndexed { r, red ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        red.forEachIndexed { i, naziv ->
                            val dan = r * 4 + i + 1
                            NeonChip(naziv, p.tjedniDan == dan, {
                                postavke.postaviTjedniDan(dan); PonavljajuciWorker.zakaziTjedni(ctx)
                            }, Modifier.weight(1f))
                        }
                        if (red.size < 4) repeat(4 - red.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { biramVrijeme = "tjedni" }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vrijeme tjednog pregleda", style = Tip.tijelo.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                Text("%02d:%02d".format(p.tjedniMinuta / 60, p.tjedniMinuta % 60), color = Neon.Cijan, fontWeight = FontWeight.Bold)
            }
        }
    }
    biramVrijeme?.let { koji ->
        val pocetno = if (koji == "tjedni") p.tjedniMinuta else p.podsjetnikMinuta
        val st = rememberTimePickerState(pocetno / 60, pocetno % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { biramVrijeme = null },
            containerColor = Neon.Sheet,
            confirmButton = {
                TextButton({
                    if (koji == "tjedni") {
                        postavke.postaviTjedniMinuta(st.hour * 60 + st.minute); PonavljajuciWorker.zakaziTjedni(ctx)
                    } else {
                        postavke.postaviPodsjetnikMinuta(st.hour * 60 + st.minute); PonavljajuciWorker.zakaziPodsjetnik(ctx)
                    }
                    biramVrijeme = null
                }) { Text("U redu", color = Neon.Zelena) }
            },
            dismissButton = { TextButton({ biramVrijeme = null }) { Text("Odustani", color = Neon.TekstSiv) } },
            text = { TimePicker(st) },
        )
    }
}

private val daniKratki = listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned")
private val daniPuni = listOf("Ponedjeljkom", "Utorkom", "Srijedom", "Četvrtkom", "Petkom", "Subotom", "Nedjeljom")

/** Kratki opis aplikacije (dodir na verziju u Postavkama). */
@Composable
fun OProgramuDijalog(onZatvori: () -> Unit) {
    AlertDialog(
        onDismissRequest = onZatvori,
        containerColor = Neon.Sheet,
        icon = { PlocicaIkone(Ikone.ui("info"), Neon.Zelena, 48.dp, sjaj = true) },
        title = { Text("SmartBudget", color = Neon.Tekst, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Verzija ${BuildConfig.VERSION_NAME}", style = Tip.oznaka)
                Text(
                    "Praćenje kućnog budžeta po obračunskim ciklusima (npr. od plaće do plaće): prihodi i troškovi, " +
                        "limiti kategorija, ponavljajući unosi, analitika, widget, izvoz u Excel i sigurnosne kopije. " +
                        "Svi podaci ostaju na uređaju.",
                    color = Neon.TekstSvijetliSiv, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
                Text("Andrej Vukić © 2026", color = Neon.Tekst, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onZatvori) { Text("Zatvori", color = Neon.Zelena) } },
    )
}
