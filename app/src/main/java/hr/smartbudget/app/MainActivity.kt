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
import hr.smartbudget.app.ui.screens.UplataCiljaSheet
import hr.smartbudget.app.ui.screens.NovcanikSheet
import hr.smartbudget.app.ui.screens.StednjaTab
import hr.smartbudget.app.ui.screens.CiljeviKartica
import hr.smartbudget.app.ui.screens.NovcaniciKartica
import hr.smartbudget.app.ui.screens.StednjaAnalitikaKartica
import hr.smartbudget.app.ui.screens.CiljeviAnalitikaKartica
import hr.smartbudget.app.ui.screens.KriptoAnalitikaKartica
import hr.smartbudget.app.ui.screens.KalkulatorValutaSheet
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
        hr.smartbudget.app.data.Alarmi.obnovi(this)
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
    /** Uplata na cilj (ili povlačenje). */
    data class UplataCilja(val c: CiljStednje, val uplata: Boolean) : Sheet
    /** Novčanik štednje u valuti; null id = novi. */
    data class NovcanikS(val id: Long?) : Sheet
    data object Kalkulator : Sheet
    /** Ulaganje u vlasništvu (dionica, ETF, obveznica, roba); null = novo. */
    data class PozicijaS(val id: Long?) : Sheet
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

    // Provjera nove verzije pri pokretanju: obavezna se uvijek nudi, odbijena neobavezna se više ne nudi sama.
    val kontekst = androidx.compose.ui.platform.LocalContext.current
    var novaVerzija by remember { mutableStateOf<hr.smartbudget.app.data.NovaVerzija?>(null) }
    LaunchedEffect(Unit) {
        runCatching { hr.smartbudget.app.data.Azuriranja.provjeri() }.getOrNull()?.let { n ->
            if (hr.smartbudget.app.data.Azuriranja.obavezno(n) || !hr.smartbudget.app.data.Azuriranja.odbijena(kontekst, n.kod)) novaVerzija = n
        }
    }
    novaVerzija?.let { n ->
        hr.smartbudget.app.ui.screens.AzuriranjeDijalog(n) {
            hr.smartbudget.app.data.Azuriranja.odbij(kontekst, n.kod)
            novaVerzija = null
        }
    }

    val tjedni by vm.tjedni.collectAsState()
    // Tjedni pregled otvoren gumbom na Početnoj (ne iz obavijesti)
    var tjedniNaZahtjev by remember { mutableStateOf(false) }
    if (tjedniNaZahtjev) tjedni?.let { TjedniDijalog(it, test = false) { tjedniNaZahtjev = false } }
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
                    VrstaObavijesti.ROK -> ekran = Ekran.POCETNA
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
    val uplate by vm.uplate.collectAsState()
    val visakProslog by vm.visakProslogCiklusa.collectAsState()
    val novcanici by vm.novcanici.collectAsState()
    val tecajevi by vm.tecajevi.collectAsState()
    val kriptoPovijest by vm.kriptoPovijest.collectAsState()
    val pozicije by vm.pozicije.collectAsState()
    val cijeneUlaganja by vm.cijeneUlaganja.collectAsState()
    val povijestUlaganja by vm.povijestUlaganja.collectAsState()
    val vrijednostUlaganja = hr.smartbudget.app.ui.screens.vrijednostUlaganja(pozicije, cijeneUlaganja, tecajevi)
    // Podsjetnici na rok ulaganja pri otvaranju aplikacije
    LaunchedEffect(Unit) { vm.provjeriRokove() }
    // Cijene ulaganja i tečaj za ukupnu štednju (jednom po pokretanju i kad se ulaganja promijene)
    LaunchedEffect(pozicije.map { it.pozicija.simbol }.toSet()) {
        if (pozicije.isNotEmpty()) { vm.osvjeziCijeneUlaganja(); if (tecajevi.hnb == null) vm.osvjeziTecajeve() }
    }
    val tekuci by vm.tekuci.collectAsState()
    val zadnjiUnosi by vm.zadnjiUnosi.collectAsState()
    val godisnji by vm.godisnji.collectAsState()
    val naziviRacuna = racuni.map { it.naziv }

    val (gore, desno) = when (ekran) {
        Ekran.POCETNA -> Neon.Zelena.copy(alpha = .10f) to Color(0x337B2CBF)
        Ekran.ANALITIKA -> Neon.Cijan.copy(alpha = .08f) to Color(0x387B2CBF)
        Ekran.BUDZETI -> Neon.Ljubicasta.copy(alpha = .18f) to Neon.Cijan.copy(alpha = .07f)
        Ekran.ULAGANJA -> Neon.Zelena.copy(alpha = .08f) to Neon.Cijan.copy(alpha = .08f)
        Ekran.POSTAVKE -> Neon.Cijan.copy(alpha = .08f) to Color(0x2E7B2CBF)
    }
    PozadinaEkrana(gore, desno, uzbuna = tekuci?.zadireURezervu == true) {
        when (ekran) {
            Ekran.POCETNA -> PocetnaEkran(
                s = pocetna,
                onPrethodni = vm::prethodniCiklus,
                onSljedeci = vm::sljedeciCiklus,
                onTrenutni = vm::trenutniCiklus,
                onTjedniPregled = { tjedniNaZahtjev = true },
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
                onObrisi = vm::obrisiTransakcije,
                onPostavke = { ekran = Ekran.POSTAVKE },
                tekuci = tekuci,
                onSpremiStanje = vm::postaviStanjeRacuna,
                stednja = {
                    StednjaTab(
                        ciljevi, uplate, novcanici, tecajevi, vm::osvjeziTecajeve, { sheet = Sheet.Prijenos },
                        ciljeviKartica = {
                            CiljeviKartica(
                                ciljevi, { sheet = Sheet.Cilj(null) }, { sheet = Sheet.Cilj(it) }, { c, u -> sheet = Sheet.UplataCilja(c, u) },
                                uplate, tecajevi, vm::osvjeziTecajeve,
                                onPodijeli = {
                                    hr.smartbudget.app.ui.screens.podijeliTekst(kontekst, "Ciljevi štednje", hr.smartbudget.app.ui.screens.tekstCiljeva(ciljevi, uplate, tecajevi))
                                },
                            )
                        },
                        novcaniciKartica = {
                            NovcaniciKartica(
                                novcanici, tecajevi, vm::osvjeziTecajeve, { sheet = Sheet.NovcanikS(null) },
                                { sheet = Sheet.NovcanikS(it.novcanik.id) }, { sheet = Sheet.Kalkulator },
                                onPodijeli = { l ->
                                    hr.smartbudget.app.ui.screens.podijeliTekst(kontekst, "Štednja u valutama", hr.smartbudget.app.ui.screens.tekstNovcanika(l, tecajevi))
                                },
                                onObrisi = { l -> l.forEach(vm::obrisiNovcanik) },
                            )
                        },
                        ulaganja = vrijednostUlaganja,
                        brojUlaganja = pozicije.count { it.kolicinaMikro > 0 },
                        ulaganjaKartica = {
                            hr.smartbudget.app.ui.screens.UlaganjaStednjaKartica(
                                pozicije, cijeneUlaganja, tecajevi,
                                { vm.osvjeziCijeneUlaganja(); vm.osvjeziTecajeve() },
                                { sheet = Sheet.PozicijaS(null) },
                                { sheet = Sheet.PozicijaS(it.pozicija.id) },
                                onPodijeli = { l ->
                                    hr.smartbudget.app.ui.screens.podijeliTekst(kontekst, "Moja ulaganja", hr.smartbudget.app.ui.screens.tekstUlaganja(l, cijeneUlaganja, tecajevi))
                                },
                                onObrisi = { l -> l.forEach(vm::obrisiPoziciju) },
                            )
                        },
                        pozicije = pozicije,
                        cijene = cijeneUlaganja,
                        tekuci = tekuci,
                        tekuciKartica = {
                            hr.smartbudget.app.ui.screens.TekuciRacunKartica(
                                tekuci, racuni, vm::postaviStanjeRacuna, vm::spremiRezervaciju,
                                vm::oslobodiRezervaciju, vm::obrisiRezervaciju, vm::postaviTekuci,
                            )
                        },
                    )
                },
            )
            Ekran.ANALITIKA -> hr.smartbudget.app.ui.ZasticeniZaslon(hr.smartbudget.app.ui.Dijelovi.CIKLUS, "Analitika") { AnalitikaEkran(
                analitika, vm::prethodniCiklus, vm::sljedeciCiklus, vm::trenutniCiklus, vm::postaviBrojCiklusa, godisnji, vm::postaviGodinu,
                imaCiljeva = ciljevi.isNotEmpty(),
                ciljevi = { StednjaZasticena { CiljeviAnalitikaKartica(ciljevi, uplate, tecajevi, vm::osvjeziTecajeve) } },
                imaStednje = ciljevi.isNotEmpty() || novcanici.isNotEmpty() || pozicije.isNotEmpty(),
                stednja = { StednjaZasticena { StednjaAnalitikaKartica(ciljevi, uplate, novcanici, tecajevi, vm::osvjeziTecajeve, vrijednostUlaganja) } },
                imaKripta = novcanici.any { it.valuta.kripto },
                kripto = { StednjaZasticena { KriptoAnalitikaKartica(novcanici, tecajevi, kriptoPovijest, vm::osvjeziTecajeve, vm::osvjeziKriptoPovijest) } },
                tekuci = {
                    val tocke by vm.tijekTekuceg.collectAsState()
                    val dana by vm.danaTekuceg.collectAsState()
                    StednjaZasticena { hr.smartbudget.app.ui.screens.TekuciAnalitikaKartica(tekuci, tocke, dana, vm::postaviDaneTekuceg) }
                },
                imaUlaganja = pozicije.any { it.kolicinaMikro > 0 },
                ulaganja = {
                    StednjaZasticena {
                        hr.smartbudget.app.ui.screens.UlaganjaAnalitikaKartica(
                            pozicije, cijeneUlaganja, povijestUlaganja, tecajevi,
                            vm::osvjeziCijeneUlaganja, vm::osvjeziTecajeve, vm::osvjeziPovijestUlaganja,
                        )
                    }
                },
            ) }
            Ekran.BUDZETI -> hr.smartbudget.app.ui.ZasticeniZaslon(hr.smartbudget.app.ui.Dijelovi.CIKLUS, "Budžeti") { BudzetiEkran(
                budzeti, ponavljajuci,
                onNova = { sheet = Sheet.Kategorije(null) },
                onUredi = { sheet = Sheet.Kategorije(it) },
                onNoviPonavljajuci = { sheet = Sheet.Pravilo(null) },
                onUrediPonavljajuci = { sheet = Sheet.Pravilo(it) },
                ciljevi = ciljevi,
                onNoviCilj = { sheet = Sheet.Cilj(null) },
                onUrediCilj = { sheet = Sheet.Cilj(it) },
                onUplatiCilj = { c, u -> sheet = Sheet.UplataCilja(c, u) },
                novcanici = novcanici,
                tecajevi = tecajevi,
                onOsvjeziTecajeve = vm::osvjeziTecajeve,
                onNoviNovcanik = { sheet = Sheet.NovcanikS(null) },
                onNovcanik = { sheet = Sheet.NovcanikS(it.novcanik.id) },
                onKalkulator = { sheet = Sheet.Kalkulator },
                uplateCiljeva = uplate,
            ) }
            Ekran.ULAGANJA -> hr.smartbudget.app.ui.screens.UlaganjaEkran()
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
        Sheet.Prijenos -> PrijenosSheet(
            racuni, vm::prijenos, { sheet = null },
            ciljevi = ciljevi, novcanici = novcanici, t = tecajevi, onOsvjezi = vm::osvjeziTecajeve,
            onNaStednju = vm::prijenosNaStednju,
            onSaStednje = vm::prijenosSaStednje,
        )
        is Sheet.PrijenosInfo -> PotvrdaBrisanja(
            "Obrisati prijenos?",
            "${s.t.opis} · ${hr.smartbudget.app.domain.Novac.eur(s.t.iznosCenti)}\n" +
                if (s.t.naStednju) "Iznos se uklanja i sa štednje (cilja ili novčanika), kao da prijenosa nije ni bilo."
                else "Brišu se oba dijela prijenosa.",
            { sheet = null },
        ) { vm.obrisiTransakciju(s.t); sheet = null }
        is Sheet.Cilj -> CiljSheet(s.c, uplate, vm::spremiCilj, vm::obrisiCilj, { sheet = null }, vm::obrisiUplatuCilja)
        is Sheet.NovcanikS -> NovcanikSheet(
            novcanici.firstOrNull { it.novcanik.id == s.id }, tecajevi,
            vm::spremiNovcanik, vm::obrisiNovcanik, vm::dodajPromjenu, vm::obrisiPromjenu,
            racuni = naziviRacuna, onIsplata = vm::isplataNovcanika, onUrediPromjenu = vm::azurirajPromjenuNovcanika,
        ) { sheet = null }
        is Sheet.PozicijaS -> hr.smartbudget.app.ui.screens.PozicijaSheet(
            pozicije.firstOrNull { it.pozicija.id == s.id }, cijeneUlaganja, tecajevi,
            vm::dodajPoziciju, vm::dodajPromjenuPozicije, vm::obrisiPromjenuPozicije, vm::obrisiPoziciju,
            racuni = naziviRacuna, onSpremi = vm::spremiPoziciju, onProdaja = vm::prodajaPozicije,
            // Kamo može ići dobit zapisa: računi, ciljevi štednje i novčanici u osnovnoj valuti
            odredista = naziviRacuna.map { "r:$it" to it } +
                ciljevi.map { "c:${it.id}" to "Cilj: ${it.naziv}" } +
                novcanici.filter { it.valuta.kod == hr.smartbudget.app.domain.Novac.kodValute }.map { "n:${it.novcanik.id}" to "Novčanik: ${it.novcanik.naziv}" },
            onSpremiZapis = vm::spremiZapis,
            onUrediPromjenu = vm::azurirajPromjenuPozicije,
        ) { sheet = null }
        Sheet.Kalkulator -> KalkulatorValutaSheet(novcanici, ciljevi, tecajevi, vm::osvjeziTecajeve, uplate) { sheet = null }
        is Sheet.UplataCilja -> UplataCiljaSheet(
            s.c, s.uplata, visakProslog, { vm.uplatiNaCilj(s.c, it) }, { sheet = null },
            uplate = uplate, t = tecajevi, onOsvjezi = vm::osvjeziTecajeve,
            onUValuti = { j, v -> vm.uplatiNaCiljUValuti(s.c, j, v) },
        )
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

/** Kartica štednje u Analitici: zaključana (bez automatskog upita) ako je zaključana štednja, zamućena ako je skrivena. */
@Composable
private fun StednjaZasticena(sadrzaj: @Composable () -> Unit) =
    hr.smartbudget.app.ui.ZasticeniDio(hr.smartbudget.app.ui.Dijelovi.STEDNJA, "Štednja", automatski = false) {
        hr.smartbudget.app.ui.SkriveniIznosiStednje(sadrzaj)
    }
