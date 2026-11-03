package hr.smartbudget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.Kategorija
import hr.smartbudget.app.data.PonavljajuciUnos
import hr.smartbudget.app.data.Obavijesti
import hr.smartbudget.app.data.VrstaObavijesti
import hr.smartbudget.app.ui.screens.ObavijestInfoDijalog
import hr.smartbudget.app.ui.screens.TjedniDijalog
import hr.smartbudget.app.data.CiljStednje
import hr.smartbudget.app.data.Racun
import hr.smartbudget.app.ui.screens.CiljSheet
import hr.smartbudget.app.ui.screens.PotvrdaBrisanja
import hr.smartbudget.app.ui.screens.PrijenosSheet
import hr.smartbudget.app.ui.screens.RacunSheet
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.ui.screens.PregledSheet
import hr.smartbudget.app.data.PonavljajuciWorker
import hr.smartbudget.app.widget.StanjeWidget
import kotlinx.coroutines.launch
import hr.smartbudget.app.data.Transakcija
import hr.smartbudget.app.ui.BudgetViewModel
import hr.smartbudget.app.ui.DonjaNavigacija
import hr.smartbudget.app.ui.Ekran
import hr.smartbudget.app.ui.EkranZakljucavanja
import hr.smartbudget.app.ui.Zakljucavanje
import hr.smartbudget.app.ui.PozadinaEkrana
import hr.smartbudget.app.ui.screens.AnalitikaEkran
import hr.smartbudget.app.ui.screens.BudzetiEkran
import hr.smartbudget.app.ui.screens.KategorijaSheet
import hr.smartbudget.app.ui.screens.PocetnaEkran
import hr.smartbudget.app.ui.screens.PostavkeEkran
import hr.smartbudget.app.ui.screens.TransakcijaSheet
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.SmartBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        PonavljajuciWorker.zakazi(this)
        // Otvoreno dodirom na obavijest: prozor s objašnjenjem (nakon otključavanja, ako je zaključano)
        Obavijesti.izNamjere(intent)
        setContent {
            SmartBudgetTheme {
                if (Zakljucavanje.zakljucano.value) EkranZakljucavanja() else Aplikacija()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Obavijesti.izNamjere(intent)
    }

    override fun onStart() {
        super.onStart()
        Zakljucavanje.naPokretanju(this)
    }

    override fun onStop() {
        super.onStop()
        Zakljucavanje.uPozadinu()
    }

    override fun onResume() {
        super.onResume()
        // Widget prati i promjenu dana/ciklusa, ne samo upise.
        BudgetRepository.get(this).let { repo ->
            repo.opseg.launch {
                repo.generirajPonavljajuce()
                StanjeWidget.osvjezi(applicationContext)
            }
        }
    }
}

/** Što je otvoreno u donjem sheetu. */
private sealed interface Sheet {
    data class Transakcije(val t: Transakcija?) : Sheet
    data class Kategorije(val k: Kategorija?) : Sheet
    /** Pregled prihoda ili rashoda ciklusa. */
    data class Pregled(val tip: TipTransakcije) : Sheet
    /** Pravilo ponavljanja; null = novo. */
    data class Pravilo(val p: PonavljajuciUnos?) : Sheet
    /** Račun; null = novi. */
    data class RacunS(val r: Racun?) : Sheet
    data object Prijenos : Sheet
    /** Dodir na prijenos u popisu: samo brisanje (oba dijela). */
    data class PrijenosInfo(val t: Transakcija) : Sheet
    /** Cilj štednje; null = novi. */
    data class Cilj(val c: CiljStednje?) : Sheet
}

@Composable
private fun Aplikacija(vm: BudgetViewModel = viewModel()) {
    var ekran by rememberSaveable { mutableStateOf(Ekran.POCETNA) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snack = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        vm.osvjeziDanas()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.poruke.collect { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(it) }
    }
    BackHandler(ekran != Ekran.POCETNA) { ekran = Ekran.POCETNA }

    val tjedni by vm.tjedni.collectAsState()
    Obavijesti.dodirnuta.value?.let { info ->
        // Tjedni pregled ima vlastiti prozor s rekapitulacijom; ostale obavijesti prozor s objašnjenjem.
        if (info.vrsta == VrstaObavijesti.TJEDNI) {
            tjedni?.let { TjedniDijalog(it, info.test) { Obavijesti.dodirnuta.value = null } }
            return@let
        }
        ObavijestInfoDijalog(
            info, vm.pocetna.value.ukupnoStanje,
            onAkcija = {
                when (info.vrsta) {
                    VrstaObavijesti.PONAVLJAJUCI, VrstaObavijesti.PROGNOZA -> ekran = Ekran.BUDZETI
                    VrstaObavijesti.MINUS -> ekran = Ekran.POCETNA
                    VrstaObavijesti.PODSJETNIK -> { ekran = Ekran.POCETNA; sheet = Sheet.Transakcije(null) }
                    VrstaObavijesti.TJEDNI -> ekran = Ekran.POCETNA
                }
            },
            onZatvori = { Obavijesti.dodirnuta.value = null },
        )
    }

    val pocetna by vm.pocetna.collectAsState()
    val analitika by vm.analitika.collectAsState()
    val budzeti by vm.budzeti.collectAsState()
    val kategorije by vm.kategorije.collectAsState()
    val ponavljajuci by vm.ponavljajuci.collectAsState()
    val racuni by vm.racuni.collectAsState()
    val stanjaRacuna by vm.stanjaRacuna.collectAsState()
    val ciljevi by vm.ciljevi.collectAsState()
    val zadnjiUnosi by vm.zadnjiUnosi.collectAsState()
    val godisnji by vm.godisnji.collectAsState()
    val naziviRacuna = racuni.map { it.naziv }

    val (gore, desno) = when (ekran) {
        Ekran.POCETNA -> Neon.Zelena.copy(alpha = .10f) to Color(0x337B2CBF)
        Ekran.ANALITIKA -> Neon.Cijan.copy(alpha = .08f) to Color(0x387B2CBF)
        Ekran.BUDZETI -> Neon.Ljubicasta.copy(alpha = .18f) to Neon.Cijan.copy(alpha = .07f)
        Ekran.POSTAVKE -> Neon.Cijan.copy(alpha = .08f) to Color(0x2E7B2CBF)
    }
    PozadinaEkrana(gore, desno) {
        when (ekran) {
            Ekran.POCETNA -> PocetnaEkran(
                s = pocetna,
                onPrethodni = vm::prethodniCiklus,
                onSljedeci = vm::sljedeciCiklus,
                onTrenutni = vm::trenutniCiklus,
                onUrediCiklus = { ekran = Ekran.POSTAVKE },
                onFilter = vm::postaviFilter,
                onTransakcija = { sheet = if (it.transakcija.jePrijenos) Sheet.PrijenosInfo(it.transakcija) else Sheet.Transakcije(it.transakcija) },
                onDodaj = { sheet = Sheet.Transakcije(null) },
                onSpremiRaspon = vm::postaviRasponCiklusa,
                onVratiZadaniRaspon = vm::vratiZadaniRaspon,
                onPregled = { sheet = Sheet.Pregled(it) },
                onPretraga = vm::postaviPretragu,
                onPretragaSvi = vm::postaviPretraguSvi,
                racuni = stanjaRacuna,
                onRacun = { sheet = Sheet.RacunS(it) },
                onNoviRacun = { sheet = Sheet.RacunS(null) },
                onPrijenos = { sheet = Sheet.Prijenos },
            )
            Ekran.ANALITIKA -> AnalitikaEkran(analitika, vm::prethodniCiklus, vm::sljedeciCiklus, vm::trenutniCiklus, vm::postaviBrojCiklusa, godisnji, vm::postaviGodinu)
            Ekran.BUDZETI -> BudzetiEkran(
                budzeti, ponavljajuci,
                onNova = { sheet = Sheet.Kategorije(null) },
                onUredi = { sheet = Sheet.Kategorije(it) },
                onNoviPonavljajuci = { sheet = Sheet.Pravilo(null) },
                onUrediPonavljajuci = { sheet = Sheet.Pravilo(it) },
                ciljevi = ciljevi,
                onNoviCilj = { sheet = Sheet.Cilj(null) },
                onUrediCilj = { sheet = Sheet.Cilj(it) },
            )
            Ekran.POSTAVKE -> PostavkeEkran(vm)
        }
        // Prijelaz prema dnu iza navigacije
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Pozadina.copy(alpha = .92f)))),
        )
        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) { data ->
            Snackbar(
                containerColor = Neon.Sheet,
                contentColor = Neon.Tekst,
                shape = RoundedCornerShape(16.dp),
            ) { Text(data.visuals.message) }
        }
        DonjaNavigacija(
            ekran, { ekran = it },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    when (val s = sheet) {
        is Sheet.Transakcije -> TransakcijaSheet(
            postojeca = s.t,
            kategorije = kategorije,
            onSpremi = vm::spremiTransakciju,
            onObrisi = vm::obrisiTransakciju,
            onZatvori = { sheet = null },
            onSpremiPonavljajuci = vm::spremiPonavljajuci,
            racuni = naziviRacuna,
            onSpremiPodijeljeno = vm::spremiPodijeljeno,
            zadnjiUnosi = zadnjiUnosi,
        )
        is Sheet.RacunS -> RacunSheet(s.r, vm::spremiRacun, vm::obrisiRacun) { sheet = null }
        Sheet.Prijenos -> PrijenosSheet(racuni, vm::prijenos) { sheet = null }
        is Sheet.PrijenosInfo -> PotvrdaBrisanja(
            "Obrisati prijenos?",
            "${s.t.opis} · ${hr.smartbudget.app.domain.Novac.eur(s.t.iznosCenti)}\nBrišu se oba dijela prijenosa.",
            { sheet = null },
        ) { vm.obrisiTransakciju(s.t); sheet = null }
        is Sheet.Cilj -> CiljSheet(s.c, vm::spremiCilj, vm::obrisiCilj) { sheet = null }
        is Sheet.Pregled -> PregledSheet(
            tip = s.tip,
            ciklus = pocetna.ciklus,
            sveTransakcije = pocetna.transakcije,
            onTransakcija = { sheet = Sheet.Transakcije(it.transakcija) },
            onZatvori = { sheet = null },
        )
        is Sheet.Pravilo -> TransakcijaSheet(
            postojeca = null,
            kategorije = kategorije,
            onSpremi = {},
            onObrisi = {},
            onZatvori = { sheet = null },
            onSpremiPonavljajuci = vm::spremiPonavljajuci,
            pravilo = s.p,
            novoPravilo = s.p == null,
            onObrisiPonavljajuci = vm::obrisiPonavljajuci,
            racuni = naziviRacuna,
        )
        is Sheet.Kategorije -> KategorijaSheet(
            postojeca = s.k,
            onSpremi = vm::spremiKategoriju,
            onObrisi = vm::obrisiKategoriju,
            onZatvori = { sheet = null },
        )
        null -> Unit
    }
}
