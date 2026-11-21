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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.smartbudget.app.data.FormatTrzista
import hr.smartbudget.app.data.Kotacija
import hr.smartbudget.app.data.OcjenaAnaliticara
import hr.smartbudget.app.data.Pozicija
import hr.smartbudget.app.data.PromjenaPozicije
import hr.smartbudget.app.data.Tecajevi
import hr.smartbudget.app.data.Trziste
import hr.smartbudget.app.data.VrstaUlaganja
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import hr.smartbudget.app.domain.Portfelj
import hr.smartbudget.app.domain.Rokovi
import hr.smartbudget.app.domain.StanjePozicije
import hr.smartbudget.app.domain.Valute
import hr.smartbudget.app.ui.CijeneUlaganja
import hr.smartbudget.app.ui.Kartica
import hr.smartbudget.app.ui.KruzniGumb
import hr.smartbudget.app.ui.LinijskiGraf
import hr.smartbudget.app.ui.Oznaka
import hr.smartbudget.app.ui.PovijestUlaganja
import hr.smartbudget.app.ui.PrimarniGumb
import hr.smartbudget.app.ui.StanjeTecajeva
import hr.smartbudget.app.ui.TonalniGumb
import hr.smartbudget.app.ui.TrakaNapretka
import hr.smartbudget.app.ui.skrivaStednju
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.abs

/*
 * "Moja ulaganja": dionice, ETF-ovi, obveznice i roba koje korisnik posjeduje (kriptovalute su u novčanicima).
 * Cijene i ocjene analitičara s Yahoo Financea, preračun u valutu aplikacije po srednjem tečaju HNB-a.
 */

private fun vrstaPozicije(p: Pozicija) = runCatching { VrstaUlaganja.valueOf(p.vrsta) }.getOrDefault(VrstaUlaganja.DIONICE)

/** Koliko 1 jedinica valute kotacije vrijedi u osnovnoj valuti (GBp = peni → funte). */
fun faktorValute(valuta: String, t: StanjeTecajeva): Double? {
    val (kod, f) = Portfelj.normalizirajValutu(valuta)
    return Tecajevi.pretvori(f, Valute.po(kod), osnovnaValuta(), t.hnb, t.kripto)
}

/** Vrijednost ulaganja u osnovnoj valuti; null dok nema cijene ili tečaja. */
fun vrijednostPozicije(s: StanjePozicije, k: Kotacija?, t: StanjeTecajeva): Double? {
    if (s.pozicija.rucno) {
        val v = s.vrijednostRucno(LocalDate.now().toEpochDay()) ?: return null
        return faktorValute(s.pozicija.valuta, t)?.let { v * it }
    }
    k ?: return null
    return faktorValute(k.valuta.ifBlank { s.pozicija.valuta }, t)?.let { s.kolicina * k.cijena * it }
}

/** Ukupna vrijednost ulaganja u osnovnoj valuti (za ukupnu štednju). */
fun vrijednostUlaganja(pozicije: List<StanjePozicije>, c: CijeneUlaganja, t: StanjeTecajeva): Double =
    pozicije.sumOf { vrijednostPozicije(it, c.kotacije[it.pozicija.simbol], t) ?: 0.0 }

/** Opis ručnog zapisa: dospijeće i godišnji prinos. */
fun opisRucnog(s: StanjePozicije): String {
    val d = LocalDate.ofEpochDay(s.pozicija.dospijeceEpochDan ?: return "")
    val prinos = s.prinosGodisnji?.let { " · " + FormatTrzista.broj(it, 2) + " % god." } ?: ""
    return if (!d.isAfter(LocalDate.now())) "Dospjelo ${HrDatum.puni(d)}$prinos" else "Dospijeće ${HrDatum.puni(d)}$prinos"
}

/** Predloženi naziv za oznaku hrvatskog državnog vrijednosnog papira. */
private fun nazivZaOznaku(oznaka: String): String = when {
    oznaka.startsWith("RHMF-T") -> "Trezorski zapis RH $oznaka"
    oznaka.startsWith("RHMF-O") -> "Obveznica RH $oznaka"
    else -> oznaka
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BiracDatumaDijalog(pocetni: LocalDate, onOdabir: (LocalDate) -> Unit, onZatvori: () -> Unit) {
    val st = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = pocetni.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onZatvori,
        confirmButton = {
            androidx.compose.material3.TextButton({
                st.selectedDateMillis?.let { onOdabir(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                onZatvori()
            }) { Text("U redu", color = Neon.Zelena) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onZatvori) { Text("Odustani", color = Neon.TekstSiv) } },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Neon.Sheet),
    ) {
        androidx.compose.material3.DatePicker(
            st,
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = Neon.Sheet, selectedDayContainerColor = Neon.Zelena, selectedDayContentColor = Neon.NaGumbu,
                todayDateBorderColor = Neon.Cijan, todayContentColor = Neon.Cijan,
            ),
        )
    }
}

@Composable
private fun OznakaOcjene(o: OcjenaAnaliticara) {
    val boja = when {
        o.vrijednost <= 2.5 -> Neon.Zelena
        o.vrijednost <= 3.5 -> Neon.Jantar
        else -> Neon.Crvena
    }
    Oznaka("Analitičari: ${o.oznaka} ${FormatTrzista.broj(o.vrijednost, 1)}", boja)
}

@Composable
private fun PromjenaDana(p: Double) {
    val boja = if (p >= 0) Neon.Zelena else Neon.Crvena
    Oznaka((if (p >= 0) "▲ " else "▼ ") + FormatTrzista.broj(abs(p), 2) + " % danas", boja)
}

// --- Kartica u Štednji ---

@Composable
fun UlaganjaStednjaKartica(
    pozicije: List<StanjePozicije>,
    c: CijeneUlaganja,
    t: StanjeTecajeva,
    onOsvjezi: () -> Unit,
    onNovo: () -> Unit,
    onOtvori: (StanjePozicije) -> Unit,
) {
    LaunchedEffect(pozicije.map { it.pozicija.simbol }.toSet()) { if (pozicije.isNotEmpty()) onOsvjezi() }
    val osnovna = osnovnaValuta()
    val sve = pozicije
    // Prodana i isplaćena ulaganja (količina 0) ne prikazuju se.
    @Suppress("NAME_SHADOWING") val pozicije = sve.filter { it.kolicinaMikro > 0 }
    Kartica(razmak = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Moja ulaganja", style = Tip.naslovKartice)
                Text("Dionice, ETF-ovi, obveznice i roba", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            if (pozicije.any { !it.pozicija.rucno }) {
                KruzniGumb("osvjezi", "Osvježi cijene", onOsvjezi, velicina = 40.dp, boja = Neon.Cijan)
                Spacer(Modifier.width(8.dp))
            }
            TonalniGumb("Dodaj", onNovo, ikona = "plus", boja = Neon.Zelena)
        }
        val zatvorene = sve.filter { it.kolicinaMikro <= 0 }
        if (pozicije.isEmpty()) {
            Text(
                "Upišite što ste kupili (npr. Apple, VWCE ili zlato) i količinu. Vrijednost se računa po tržišnoj cijeni s Yahoo Financea " +
                    "i preračunava u ${osnovna.kod} po srednjem tečaju HNB-a. Trezorske zapise i obveznice RH (npr. RHMF-T-722N) unesite ručno, s iznosom i datumom dospijeća.",
                style = Tip.malo,
            )
            ZatvorenaUlaganja(zatvorene, onOtvori)
            return@Kartica
        }
        var ukupno = 0.0
        var potpuno = true
        pozicije.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = Neon.RubSlab)
            val k = c.kotacije[s.pozicija.simbol]
            val v = vrijednostPozicije(s, k, t)
            if (v != null) ukupno += v else if (s.kolicinaMikro != 0L) potpuno = false
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Otvori ulaganje") { onOtvori(s) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarSimbola(s.pozicija.simbol, 38.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.pozicija.naziv, style = Tip.tijelo.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (s.pozicija.rucno) opisRucnog(s)
                        else "${Portfelj.kolicina(s.kolicinaMikro)} × " + (k?.let { FormatTrzista.cijena(it.cijena, it.valuta) } ?: if (c.ucitava) "…" else "—") +
                            (s.pozicija.dospijeceEpochDan?.let { " · " + Rokovi.opis(LocalDate.ofEpochDay(it)) } ?: ""),
                        style = Tip.malo, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v?.let { Valute.iznos(it, osnovna) } ?: "—", style = Tip.iznos.copy(fontSize = 15.sp), color = Neon.Tekst, maxLines = 1, softWrap = false, modifier = Modifier.skrivaStednju())
                    k?.let { PromjenaDana(it.promjenaPosto) }
                }
            }
        }
        HorizontalDivider(color = Neon.RubSlab)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ukupno", style = Tip.tijelo.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text((if (potpuno) "≈ " else "≥ ") + Valute.iznos(ukupno, osnovna), style = Tip.iznos.copy(fontSize = 17.sp), color = Neon.Zelena, modifier = Modifier.skrivaStednju())
        }
        ZatvorenaUlaganja(zatvorene, onOtvori)
        IzvorCijena(c, t)
    }
}

/** Prodana, isplaćena ili reinvestirana ulaganja: sklopivi popis (otvaranje = povijest i brisanje). */
@Composable
private fun ZatvorenaUlaganja(zatvorene: List<StanjePozicije>, onOtvori: (StanjePozicije) -> Unit) {
    if (zatvorene.isEmpty()) return
    var otvoreno by rememberSaveable { mutableStateOf(false) }
    Text(
        (if (otvoreno) "▾ " else "▸ ") + "Zatvoreno (prodano ili isplaćeno): ${zatvorene.size}",
        style = Tip.oznaka, modifier = Modifier.fillMaxWidth().clickable { otvoreno = !otvoreno }.padding(vertical = 6.dp),
    )
    if (otvoreno) zatvorene.forEach { s ->
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Otvori") { onOtvori(s) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarSimbola(s.pozicija.simbol, 30.dp)
            Text(s.pozicija.naziv, style = Tip.malo, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(s.promjene.firstOrNull()?.let { HrDatum.puni(LocalDate.ofEpochDay(it.epochDan)) } ?: "", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        }
    }
}

@Composable
private fun IzvorCijena(c: CijeneUlaganja, t: StanjeTecajeva) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            c.ucitava -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Neon.Cijan, strokeWidth = 2.dp)
                Text("Dohvaćam tržišne cijene…", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            c.dohvaceno > 0 -> Text(
                "Cijene: Yahoo Finance (s odgodom), ${HrDatum.vrijeme(java.time.Instant.ofEpochMilli(c.dohvaceno).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())}",
                style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp),
            )
        }
        c.greska?.let { Text(it, color = Neon.Jantar, fontSize = 11.sp) }
        IzvorTecajeva(t, kripto = false)
    }
}

// --- Unos i uređivanje ---

/** Novo ulaganje (null): pretraga po nazivu, oznaci ili ISIN-u i količina. Postojeće: kupnja/prodaja, povijest, brisanje. */
@Composable
fun PozicijaSheet(
    s: StanjePozicije?,
    c: CijeneUlaganja,
    t: StanjeTecajeva,
    onDodaj: (Pozicija, PromjenaPozicije?) -> Unit,
    onPromjena: (PromjenaPozicije) -> Unit,
    onObrisiPromjenu: (PromjenaPozicije) -> Unit,
    onObrisi: (Pozicija) -> Unit,
    /** Nazivi računa na koje može ići novac od prodaje ili isplate. */
    racuni: List<String> = emptyList(),
    /** Spremanje promjene ulaganja (npr. roka). */
    onSpremi: (Pozicija) -> Unit = {},
    /** Prodaja ili isplata: promjena, račun (null = ne na račun), iznos u centima i opis prijenosa. */
    onProdaja: (PromjenaPozicije, String?, Long?, String) -> Unit = { pr, _, _, _ -> onPromjena(pr) },
    onZatvori: () -> Unit,
) {
    val osnovna = osnovnaValuta()
    var upit by remember { mutableStateOf("") }
    var rezultati by remember { mutableStateOf<List<Trziste.Pronadeno>>(emptyList()) }
    var trazim by remember { mutableStateOf(false) }
    var odabrano by remember { mutableStateOf<Trziste.Pronadeno?>(null) }
    var kotOdabranog by remember { mutableStateOf<Kotacija?>(null) }
    var kupnja by remember { mutableStateOf(true) }
    var kolicina by remember { mutableStateOf("") }
    var placeno by remember { mutableStateOf("") }
    var napomena by remember { mutableStateOf("") }
    var greska by remember { mutableStateOf<String?>(null) }
    var potvrda by remember { mutableStateOf(false) }
    var brisemPromjenu by remember { mutableStateOf<PromjenaPozicije?>(null) }
    // Ručni unos državnog zapisa ili obveznice (nema tržišne cijene)
    var rucniUnos by remember { mutableStateOf(false) }
    var oznaka by remember { mutableStateOf("") }
    var nazivRucno by remember { mutableStateOf("") }
    var ulozenoRucno by remember { mutableStateOf("") }
    var nominalRucno by remember { mutableStateOf("") }
    var kupljeno by remember { mutableStateOf(LocalDate.now()) }
    var dospijece by remember { mutableStateOf<LocalDate?>(null) }
    var biramDatum by remember { mutableIntStateOf(0) } // 1 = kupnja, 2 = dospijeće, 3 = rok novog, 4 = rok postojećeg
    var rok by remember { mutableStateOf<LocalDate?>(null) }
    // Kamo ide novac od prodaje ili isplate (null = ne na račun)
    var racun by remember { mutableStateOf(racuni.firstOrNull { it == hr.smartbudget.app.data.NacinPlacanja.TEKUCI } ?: racuni.firstOrNull()) }
    var iznosIsplate by remember { mutableStateOf("") }
    var isplata by remember { mutableStateOf(false) }
    var reinvest by remember { mutableStateOf(false) }

    /** Sprema ručni zapis; kod reinvestiranja zatvara stari (bez priljeva na račun). */
    fun spremiRucni(zatvori: StanjePozicije?) {
        val ul = Novac.parsiraj(ulozenoRucno)
        val nom = Novac.parsiraj(nominalRucno)
        val d = dospijece
        when {
            oznaka.isBlank() -> greska = "Unesite oznaku (npr. RHMF-T-722N)."
            ul == null || ul <= 0 -> greska = "Unesite uloženi iznos."
            nom == null || nom <= 0 -> greska = "Unesite iznos koji dobivate pri dospijeću."
            d == null -> greska = "Odaberite datum dospijeća."
            !d.isAfter(kupljeno) -> greska = "Dospijeće mora biti nakon datuma kupnje."
            else -> {
                val o = oznaka.trim()
                onDodaj(
                    Pozicija(
                        simbol = o, naziv = nazivRucno.trim().ifBlank { nazivZaOznaku(o) }, vrsta = VrstaUlaganja.OBVEZNICE.name,
                        valuta = osnovna.kod, nominalCenti = nom, dospijeceEpochDan = d.toEpochDay(),
                    ),
                    PromjenaPozicije(pozicijaId = 0, kolicinaMikro = 1_000_000, epochDan = kupljeno.toEpochDay(), ulozenoCenti = ul),
                )
                zatvori?.let {
                    onProdaja(
                        PromjenaPozicije(pozicijaId = it.pozicija.id, kolicinaMikro = -it.kolicinaMikro, epochDan = LocalDate.now().toEpochDay(), napomena = "reinvestirano u $o"),
                        null, null, "",
                    )
                }
                onZatvori()
            }
        }
    }

    // Pretraga dok se tipka (s kratkom odgodom)
    LaunchedEffect(upit) {
        if (s != null || rucniUnos || upit.trim().length < 2) { rezultati = emptyList(); return@LaunchedEffect }
        delay(400)
        trazim = true
        rezultati = runCatching { Trziste.pretrazi(upit) }.getOrDefault(emptyList())
        trazim = false
    }
    LaunchedEffect(odabrano) {
        kotOdabranog = null
        odabrano?.let { o -> kotOdabranog = runCatching { Trziste.kotacijeUlaganja(listOf(o.simbol))[o.simbol] }.getOrNull() }
    }

    NeonSheet(onZatvori) {
        ZaglavljeSheeta(
            when {
                reinvest -> "Reinvestiraj"
                s == null -> if (rucniUnos) "Državni zapis / obveznica" else "Novo ulaganje"
                else -> s.pozicija.naziv
            },
            onZatvori,
        )
        if ((s == null && rucniUnos) || (s != null && reinvest)) {
            if (reinvest) Text("Novi zapis kupljen iznosom dospjelog; stari se zatvara bez priljeva na račun.", color = Neon.Cijan, fontSize = 13.sp, lineHeight = 18.sp)
            RucniUnosForma(
                oznaka, { oznaka = it.uppercase() },
                nazivRucno, { nazivRucno = it }, ulozenoRucno, { ulozenoRucno = it; greska = null }, nominalRucno, { nominalRucno = it; greska = null },
                kupljeno, dospijece, { biramDatum = 1 }, { biramDatum = 2 }, greska,
                onNatrag = { rucniUnos = false; reinvest = false; greska = null },
            ) { spremiRucni(if (reinvest) s else null) }
        } else if (s == null) {
            val o = odabrano
            if (o == null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OznakaPolja("Što ste kupili?")
                    NeonPolje(upit, { upit = it }, Modifier.fillMaxWidth(), placeholder = "Naziv, oznaka ili ISIN (npr. Apple, VWCE, zlato)")
                }
                when {
                    trazim -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Neon.Cijan, strokeWidth = 2.dp)
                        Text("Tražim…", style = Tip.malo)
                    }
                    upit.trim().length >= 2 && rezultati.isEmpty() -> Text(
                        "Nema rezultata. Probajte oznaku (npr. AAPL) ili ISIN. Kriptovalute dodajte kao novčanik u Štednji u valutama.",
                        style = Tip.malo,
                    )
                }
                Column {
                    rezultati.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Odaberi ${r.naziv}") { odabrano = r; greska = null }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarSimbola(r.simbol, 36.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(r.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${r.simbol} · ${r.vrsta.naziv}" + (r.burza?.let { " · $it" } ?: ""), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
                            }
                        }
                    }
                }
                // Hrvatski trezorski zapisi i obveznice nisu na burzama s javnim cijenama: ručni unos.
                val rh = upit.trim().uppercase().startsWith("RHMF")
                if (rh) {
                    Text(
                        "Trezorski zapisi i obveznice Republike Hrvatske nemaju javnu tržišnu cijenu. Unesite ih ručno: " +
                            "aplikacija iz uloženog iznosa, iznosa pri dospijeću i datuma računa vrijednost i prinos.",
                        color = Neon.Jantar, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                TonalniGumb(
                    "Unesi ručno (trezorski zapis, obveznica RH)", {
                        rucniUnos = true; greska = null
                        if (rh) { oznaka = upit.trim().uppercase(); nazivRucno = nazivZaOznaku(oznaka) }
                    },
                    Modifier.fillMaxWidth(), ikona = "edit", boja = if (rh) Neon.Jantar else Neon.Cijan,
                )
            } else {
                // Odabrani instrument i trenutna cijena
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Neon.Ispuna).border(1.dp, Neon.RubSlab, RoundedCornerShape(18.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarSimbola(o.simbol, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.naziv, style = Tip.tijelo, maxLines = 2)
                        Text(
                            "${o.simbol} · " + (kotOdabranog?.let { FormatTrzista.cijena(it.cijena, it.valuta) } ?: "dohvaćam cijenu…"),
                            style = Tip.malo, maxLines = 1,
                        )
                    }
                    Text("Promijeni", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { odabrano = null })
                }
                kotOdabranog?.ocjena?.let { OznakaOcjene(it) }
            }
        } else if (s.pozicija.rucno) {
            KarticaRucnog(s)
            if (s.kolicinaMikro <= 0) {
                Text("Zapis je isplaćen ili reinvestiran.", style = Tip.malo)
            } else {
                val danas = LocalDate.now()
                val dospjelo = s.pozicija.dospijeceEpochDan?.let { !LocalDate.ofEpochDay(it).isAfter(danas) } == true
                val predlozeno = if (dospjelo) s.pozicija.nominalCenti ?: 0 else kotlin.math.round((s.vrijednostRucno(danas.toEpochDay()) ?: 0.0) * 100).toLong()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalniGumb("Isplati", {
                        isplata = !isplata; iznosIsplate = Novac.broj(predlozeno); greska = null
                    }, Modifier.weight(1f), ikona = "download", boja = Neon.Zelena)
                    TonalniGumb("Reinvestiraj", {
                        reinvest = true; isplata = false; greska = null
                        oznaka = ""; nazivRucno = ""; nominalRucno = ""
                        ulozenoRucno = Novac.broj(predlozeno)
                        kupljeno = s.pozicija.dospijeceEpochDan?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(danas) } ?: danas
                        dospijece = null
                    }, Modifier.weight(1f), ikona = "repeat", boja = Neon.Cijan)
                }
                if (isplata) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OznakaPolja("Isplaćeni iznos (${osnovna.kod})")
                        NeonPolje(iznosIsplate, { iznosIsplate = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                    }
                    OdredisteNovca(racuni, racun) { racun = it }
                    PrimarniGumb("Potvrdi isplatu", onClick = {
                        onProdaja(
                            PromjenaPozicije(pozicijaId = s.pozicija.id, kolicinaMikro = -s.kolicinaMikro, epochDan = danas.toEpochDay(), napomena = "isplata"),
                            racun, Novac.parsiraj(iznosIsplate), "Isplata: ${s.pozicija.naziv}",
                        )
                        onZatvori()
                    }, ikona = "check")
                }
            }
        } else {
            // Stanje postojećeg ulaganja
            val k = c.kotacije[s.pozicija.simbol]
            val v = vrijednostPozicije(s, k, t)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.Zelena.copy(alpha = .06f))
                    .border(1.dp, Neon.Zelena.copy(alpha = .22f), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarSimbola(s.pozicija.simbol, 36.dp)
                    Text("${s.pozicija.simbol} · ${vrstaPozicije(s.pozicija).naziv}", style = Tip.oznaka, modifier = Modifier.weight(1f))
                    k?.let { PromjenaDana(it.promjenaPosto) }
                }
                Text(v?.let { Valute.iznos(it, osnovna) } ?: "—", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, maxLines = 1)
                Text(
                    "${Portfelj.kolicina(s.kolicinaMikro)} kom. × " + (k?.let { FormatTrzista.cijena(it.cijena, it.valuta) } ?: "—"),
                    style = Tip.iznos.copy(fontSize = 15.sp), color = Neon.TekstSvijetliSiv,
                )
                if (v != null && s.ulozeno != null && s.ulozenoPotpuno) {
                    val ul = s.ulozeno!! / 100.0
                    val d = v - ul
                    Text(
                        "Uloženo ${Valute.iznos(ul, osnovna)} · " + (if (d >= 0) "dobit " else "gubitak ") + Valute.iznos(abs(d), osnovna) +
                            if (ul > 0) " (${if (d >= 0) "+" else Novac.MINUS}${Novac.postotak(abs(d) * 100 / ul)})" else "",
                        color = if (d >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp,
                    )
                }
                k?.ocjena?.let { OznakaOcjene(it) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.pozicija.dospijeceEpochDan?.let { "Rok ulaganja: " + Rokovi.opis(LocalDate.ofEpochDay(it)) } ?: "Rok ulaganja nije postavljen",
                    style = Tip.malo, modifier = Modifier.weight(1f),
                )
                Text(
                    if (s.pozicija.dospijeceEpochDan == null) "Postavi" else "Promijeni", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { biramDatum = 4 }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
                if (s.pozicija.dospijeceEpochDan != null) Text(
                    "Ukloni", color = Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSpremi(s.pozicija.copy(dospijeceEpochDan = null)) }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            Segmenti(listOf("Kupnja", "Prodaja"), if (kupnja) 0 else 1, { kupnja = it == 0; greska = null }, boje = listOf(Neon.Zelena, Neon.Jantar))
        }

        if ((s != null && !s.pozicija.rucno) || (s == null && !rucniUnos && odabrano != null)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OznakaPolja("Količina (komada, dopušteni dijelovi)")
                NeonPolje(
                    kolicina, { kolicina = it; greska = null }, Modifier.fillMaxWidth(), placeholder = "npr. 10 ili 0,5", tipkovnica = KeyboardType.Decimal,
                    stil = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (kupnja) Neon.Zelena else Neon.Jantar, fontFeatureSettings = "tnum"),
                )
                // Procjena vrijednosti upisane količine
                val kot = if (s != null) c.kotacije[s.pozicija.simbol] else kotOdabranog
                val kol = Portfelj.parsirajKolicinu(kolicina)
                if (kot != null && kol != null && kol > 0) {
                    faktorValute(kot.valuta, t)?.let { f ->
                        Text("≈ ${Valute.iznos(Portfelj.uBroj(kol) * kot.cijena * f, osnovna)} po današnjoj cijeni", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
                    }
                }
            }
            if (kupnja) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OznakaPolja("Plaćeno ukupno (${osnovna.kod}, opcionalno, za izračun dobiti)")
                    NeonPolje(placeno, { placeno = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OznakaPolja("Dobiveno od prodaje (${osnovna.kod})")
                    NeonPolje(iznosIsplate, { iznosIsplate = it }, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
                }
                OdredisteNovca(racuni, racun) { racun = it }
            }
            if (s == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GumbPolja("calendar", rok?.let { "Rok ${HrDatum.puni(it)}" } ?: "Rok ulaganja (opcionalno)", Modifier.weight(1f)) { biramDatum = 3 }
                    if (rok != null) Text("Ukloni", color = Neon.TekstSiv, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { rok = null }.padding(6.dp))
                }
            }
            NeonPolje(napomena, { napomena = it }, Modifier.fillMaxWidth(), placeholder = "Napomena (opcionalno, npr. broker)")
            greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
            PrimarniGumb(
                when {
                    s == null -> "Dodaj ulaganje"
                    kupnja -> "Dodaj kupnju"
                    else -> "Dodaj prodaju"
                },
                onClick = {
                    val kol = Portfelj.parsirajKolicinu(kolicina)
                    when {
                        kol == null || kol <= 0 -> greska = "Unesite količinu (najviše 6 decimala)."
                        s != null && !kupnja && kol > s.kolicinaMikro -> greska = "Imate samo ${Portfelj.kolicina(s.kolicinaMikro)} kom."
                        else -> {
                            val promjena = PromjenaPozicije(
                                pozicijaId = s?.pozicija?.id ?: 0, kolicinaMikro = if (kupnja) kol else -kol,
                                epochDan = LocalDate.now().toEpochDay(),
                                ulozenoCenti = if (kupnja) Novac.parsiraj(placeno)?.takeIf { it > 0 } else null,
                                napomena = napomena.trim(),
                            )
                            if (s == null) {
                                val o = odabrano!!
                                onDodaj(
                                    Pozicija(simbol = o.simbol, naziv = o.naziv, vrsta = o.vrsta.name, valuta = kotOdabranog?.valuta ?: "USD", dospijeceEpochDan = rok?.toEpochDay()),
                                    promjena,
                                )
                                onZatvori()
                            } else if (!kupnja) {
                                // Iznos: upisani ili procjena po današnjoj cijeni
                                val k = c.kotacije[s.pozicija.simbol]
                                val procjena = k?.let { kk -> faktorValute(kk.valuta, t)?.let { kotlin.math.round(Portfelj.uBroj(kol) * kk.cijena * it * 100).toLong() } }
                                onProdaja(promjena, racun, Novac.parsiraj(iznosIsplate)?.takeIf { it > 0 } ?: procjena, "Prodaja: ${s.pozicija.naziv}")
                                kolicina = ""; iznosIsplate = ""; napomena = ""
                            } else {
                                onPromjena(promjena)
                                kolicina = ""; placeno = ""; napomena = ""
                            }
                        }
                    }
                },
                ikona = "check",
            )
        }

        if (s != null) {
            if (s.promjene.isNotEmpty() && !s.pozicija.rucno) {
                Column {
                    OznakaPolja("Povijest · dodirnite za brisanje")
                    s.promjene.take(15).forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Neon.RubSlab)
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Obriši promjenu") { brisemPromjenu = p }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(HrDatum.puni(LocalDate.ofEpochDay(p.epochDan)) + if (p.kolicinaMikro >= 0) " · kupnja" else " · prodaja", style = Tip.tijelo.copy(fontSize = 14.sp))
                                val dodatno = listOfNotNull(p.napomena.takeIf { it.isNotBlank() }, p.ulozenoCenti?.let { "plaćeno ${Valute.iznos(it, osnovna)}" })
                                if (dodatno.isNotEmpty()) Text(dodatno.joinToString(" · "), style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1)
                            }
                            Text(
                                (if (p.kolicinaMikro >= 0) "+" else Novac.MINUS) + Portfelj.kolicina(abs(p.kolicinaMikro)) + " kom.",
                                style = Tip.iznos.copy(fontSize = 14.sp), color = if (p.kolicinaMikro >= 0) Neon.Zelena else Neon.Jantar,
                            )
                        }
                    }
                }
            }
            TonalniGumb("Obriši ulaganje", { potvrda = true }, Modifier.fillMaxWidth(), boja = Neon.Crvena)
        }
        Text(
            "Cijene s Yahoo Financea (s odgodom), preračun u ${osnovna.kod} po srednjem tečaju HNB-a. Aplikacija ne trguje i ne povezuje se s vašim brokerom.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
        )
    }
    when (biramDatum) {
        1 -> BiracDatumaDijalog(kupljeno, { kupljeno = it; greska = null }) { biramDatum = 0 }
        2 -> BiracDatumaDijalog(dospijece ?: kupljeno.plusMonths(12), { dospijece = it; greska = null }) { biramDatum = 0 }
        3 -> BiracDatumaDijalog(rok ?: LocalDate.now().plusYears(1), { rok = it }) { biramDatum = 0 }
        4 -> s?.let { st ->
            BiracDatumaDijalog(st.pozicija.dospijeceEpochDan?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusYears(1), {
                onSpremi(st.pozicija.copy(dospijeceEpochDan = it.toEpochDay()))
            }) { biramDatum = 0 }
        }
    }
    if (potvrda && s != null) {
        PotvrdaBrisanja("Obrisati ulaganje?", "\"${s.pozicija.naziv}\" i sve njegove kupnje i prodaje bit će obrisane.", { potvrda = false }) {
            onObrisi(s.pozicija); potvrda = false; onZatvori()
        }
    }
    brisemPromjenu?.let { p ->
        PotvrdaBrisanja("Obrisati promjenu?", "${HrDatum.puni(LocalDate.ofEpochDay(p.epochDan))} · ${Portfelj.kolicina(abs(p.kolicinaMikro))} kom.", { brisemPromjenu = null }) {
            onObrisiPromjenu(p); brisemPromjenu = null
        }
    }
}

// --- Analitika ---

@Composable
private fun PlocicaVrijednosti(naziv: String, vrijednost: String, boja: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Neon.Ispuna).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Iznos s valutom u jednom redu: po potrebi se malo smanji.
        androidx.compose.foundation.text.BasicText(
            vrijednost, maxLines = 1,
            style = TextStyle(color = boja, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
        )
        Text(naziv, style = Tip.oznaka.copy(fontSize = 11.sp), maxLines = 1)
    }
}

/**
 * Analitika ulaganja: ukupna vrijednost i promjena danas, dobit u odnosu na uloženo, kretanje vrijednosti
 * kroz razdoblje (ukupno ili odabrano ulaganje), udjeli i današnja ocjena analitičara za svako ulaganje.
 */
@Composable
fun UlaganjaAnalitikaKartica(
    pozicije: List<StanjePozicije>,
    c: CijeneUlaganja,
    povijest: PovijestUlaganja,
    t: StanjeTecajeva,
    onOsvjeziCijene: () -> Unit,
    onOsvjeziTecajeve: () -> Unit,
    onPovijest: (Int) -> Unit,
) {
    val aktivne = pozicije.filter { it.kolicinaMikro > 0 }
    if (aktivne.isEmpty()) return
    val razdoblja = listOf(30, 90, 365)
    var odabrano by rememberSaveable { mutableIntStateOf(1) }
    val dana = razdoblja[odabrano]
    val simboli = aktivne.filter { !it.pozicija.rucno }.map { it.pozicija.simbol }.toSet()
    LaunchedEffect(dana, simboli) {
        if (t.hnb == null) onOsvjeziTecajeve()
        if (c.kotacije.keys.containsAll(simboli).not()) onOsvjeziCijene()
        if (simboli.isNotEmpty() && (povijest.dana != dana || !povijest.cijene.keys.containsAll(simboli))) onPovijest(dana)
    }
    val osnovna = osnovnaValuta()
    var odabraniSimbol by rememberSaveable { mutableStateOf<String?>(null) }
    val odabrana = aktivne.firstOrNull { it.pozicija.simbol == odabraniSimbol }
    val danas = LocalDate.now()
    val tocke = remember(povijest, aktivne, odabrana, t.hnb) {
        Portfelj.vrijednostPoDanima(
            odabrana?.let { listOf(it) } ?: aktivne, povijest.cijene,
            { faktorValute(it, t) }, danas.minusDays(dana.toLong() - 1), danas,
        )
    }
    val vrijednosti = aktivne.map { it to vrijednostPozicije(it, c.kotacije[it.pozicija.simbol], t) }
    val ukupno = vrijednosti.sumOf { it.second ?: 0.0 }
    val promjenaDanas = vrijednosti.sumOf { (s, v) ->
        val p = c.kotacije[s.pozicija.simbol]?.promjenaPosto ?: 0.0
        (v ?: 0.0) - (v ?: 0.0) / (1 + p / 100)
    }
    val ulozeno = aktivne.takeIf { l -> l.all { it.ulozenoPotpuno && it.ulozeno != null } }?.sumOf { it.ulozeno!! }?.div(100.0)

    Kartica(razmak = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Moja ulaganja", style = Tip.naslovKartice)
                Text("Dionice, ETF-ovi, obveznice i roba · Yahoo Finance", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
            }
            KruzniGumb("osvjezi", "Osvježi cijene", { onOsvjeziCijene(); onOsvjeziTecajeve(); onPovijest(dana) }, velicina = 38.dp, boja = Neon.Zelena)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Valute.iznos(ukupno, osnovna), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Zelena, modifier = Modifier.skrivaStednju())
            if (ukupno > 0 && c.kotacije.isNotEmpty()) {
                val p = promjenaDanas * 100 / (ukupno - promjenaDanas).coerceAtLeast(.01)
                Text(
                    (if (promjenaDanas >= 0) "▲ " else "▼ ") + Novac.postotak(abs(p)) + " danas",
                    color = if (promjenaDanas >= 0) Neon.Zelena else Neon.Crvena, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        ulozeno?.takeIf { it > 0 }?.let { ul ->
            val d = ukupno - ul
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlocicaVrijednosti("Uloženo", Valute.iznos(ul, osnovna), Neon.Tekst, Modifier.weight(1f))
                PlocicaVrijednosti(if (d >= 0) "Dobit" else "Gubitak", Valute.iznos(abs(d), osnovna), if (d >= 0) Neon.Zelena else Neon.Crvena, Modifier.weight(1f))
                PlocicaVrijednosti("Prinos", (if (d >= 0) "+" else Novac.MINUS) + Novac.postotak(abs(d) * 100 / ul), if (d >= 0) Neon.Zelena else Neon.Crvena, Modifier.weight(1f))
            }
        }
        Segmenti(listOf("30 dana", "90 dana", "1 godina"), odabrano, { odabrano = it }, boje = List(3) { Neon.Zelena })
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (odabrana != null) AvatarSimbola(odabrana.pozicija.simbol, 34.dp)
            Text(
                odabrana?.let { if (it.pozicija.rucno) it.pozicija.naziv else "${it.pozicija.naziv} · ${Portfelj.kolicina(it.kolicinaMikro)} kom." } ?: "Ukupno ulaganja",
                style = Tip.tijelo.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).padding(start = if (odabrana != null) 8.dp else 0.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (odabrana != null) KruzniGumb("restore", "Prikaži ukupno", { odabraniSimbol = null }, velicina = 32.dp, boja = Neon.Cijan)
        }
        when {
            tocke.size >= 2 -> {
                LinijskiGraf(tocke, if (odabrana != null) Neon.Cijan else Neon.Zelena, { Valute.iznos(it, osnovna) })
                val prvi = tocke.first()
                val z = tocke.last().second
                if (prvi.second > 0) {
                    val p = (z - prvi.second) * 100 / prvi.second
                    Text(
                        "Od ${HrDatum.puni(prvi.first)} vrijednost " + (odabrana?.let { "ulaganja ${kratkiSimbol(it.pozicija.simbol)}" } ?: "vaših ulaganja") + " " +
                            (if (p >= 0) "porasla je" else "pala je") + " ${Novac.postotak(abs(p))} (${if (z - prvi.second >= 0) "+" else Novac.MINUS}${Valute.iznos(abs(z - prvi.second), osnovna)}).",
                        color = Neon.TekstSvijetliSiv, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            povijest.ucitava -> Text("Dohvaćam povijest cijena…", style = Tip.malo)
            else -> Text(povijest.greska ?: "Graf će se pokazati kad bude dostupna povijest cijena.", style = Tip.malo)
        }
        Text(
            "Graf prikazuje trenutne količine po cijenama kroz razdoblje (kretanje tržišta, bez skokova od kupnji); valute po današnjem tečaju.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
        )
        HorizontalDivider(color = Neon.RubSlab)
        if (aktivne.size > 1) Text("Dodirnite ulaganje za njegov graf", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp))
        vrijednosti.sortedByDescending { it.second ?: 0.0 }.forEach { (s, v) ->
            val k = c.kotacije[s.pozicija.simbol]
            val jeOdabrana = s.pozicija.simbol == odabraniSimbol
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (jeOdabrana) Neon.Cijan.copy(alpha = .1f) else Color.Transparent)
                    .clickable(onClickLabel = "Prikaži graf") { odabraniSimbol = if (jeOdabrana) null else s.pozicija.simbol }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarSimbola(s.pozicija.simbol, 34.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.pozicija.naziv, style = Tip.tijelo.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (s.pozicija.rucno) opisRucnog(s)
                            else "${Portfelj.kolicina(s.kolicinaMikro)} × " + (k?.let { FormatTrzista.cijena(it.cijena, it.valuta) } ?: "—"),
                            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal), maxLines = 1,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(v?.let { Valute.iznos(it, osnovna) } ?: "—", style = Tip.iznos.copy(fontSize = 14.sp), color = Neon.Tekst, maxLines = 1)
                        k?.let { PromjenaDana(it.promjenaPosto) }
                    }
                }
                // Današnja preporuka analitičara (dionice; ETF-ovi i roba je obično nemaju)
                k?.ocjena?.let { OznakaOcjene(it) }
                if (aktivne.size > 1 && v != null && ukupno > 0) {
                    TrakaNapretka((v / ukupno).toFloat(), Brush.horizontalGradient(listOf(Neon.Zelena, Neon.Cijan)), Neon.Zelena, visina = 4.dp)
                }
            }
        }
        IzvorCijena(c, t)
        Text(
            "Ocjene su konsenzus vanjskih analitičara na današnji dan (1 = jaka kupnja, 5 = prodaja) i nisu savjet za ulaganje.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
        )
    }
}


/** Prikaz ručno unesenog zapisa ili obveznice: vrijednost danas, uloženo, iznos i datum dospijeća, prinos i protek vremena. */
@Composable
private fun KarticaRucnog(s: StanjePozicije) {
    val v = Valute.po(s.pozicija.valuta)
    val danas = LocalDate.now()
    val dosp = LocalDate.ofEpochDay(s.pozicija.dospijeceEpochDan!!)
    val kupljeno = LocalDate.ofEpochDay(s.kupljenoEpochDan ?: danas.toEpochDay())
    val ulozeno = (s.ulozeno ?: 0) / 100.0
    val nominal = (s.pozicija.nominalCenti ?: 0) / 100.0
    val sada = s.vrijednostRucno(danas.toEpochDay()) ?: ulozeno
    val ukupnoDana = java.time.temporal.ChronoUnit.DAYS.between(kupljeno, dosp).coerceAtLeast(1)
    val proslo = java.time.temporal.ChronoUnit.DAYS.between(kupljeno, danas).coerceIn(0, ukupnoDana)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Neon.LjubicastaSvijetla.copy(alpha = .06f))
            .border(1.dp, Neon.LjubicastaSvijetla.copy(alpha = .25f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarSimbola(s.pozicija.simbol, 36.dp)
            Text("${s.pozicija.simbol} · ručni unos", style = Tip.oznaka, modifier = Modifier.weight(1f))
        }
        Text(Valute.iznos(sada, v), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Neon.Tekst, maxLines = 1)
        Text("Vrijednost danas (uloženo + obračunati prinos)", style = Tip.oznaka.copy(fontWeight = FontWeight.Normal))
        TrakaNapretka(
            (proslo.toFloat() / ukupnoDana).coerceIn(0f, 1f), Brush.horizontalGradient(listOf(Neon.LjubicastaSvijetla, Neon.Zelena)), Neon.LjubicastaSvijetla, visina = 6.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Kupljeno ${HrDatum.puni(kupljeno)}", style = Tip.oznaka.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal))
            Text(
                if (dosp.isAfter(danas)) "Dospijeće ${HrDatum.puni(dosp)} · još ${java.time.temporal.ChronoUnit.DAYS.between(danas, dosp)} dana" else "Dospjelo ${HrDatum.puni(dosp)}",
                style = Tip.oznaka.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlocicaVrijednosti("Uloženo", Valute.iznos(ulozeno, v), Neon.Tekst, Modifier.weight(1f))
            PlocicaVrijednosti("Pri dospijeću", Valute.iznos(nominal, v), Neon.Zelena, Modifier.weight(1f))
            PlocicaVrijednosti("Prinos god.", s.prinosGodisnji?.let { FormatTrzista.broj(it, 2) + " %" } ?: "—", Neon.Zelena, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RucniUnosForma(
    oznaka: String, onOznaka: (String) -> Unit,
    naziv: String, onNaziv: (String) -> Unit,
    ulozeno: String, onUlozeno: (String) -> Unit,
    nominal: String, onNominal: (String) -> Unit,
    kupljeno: LocalDate, dospijece: LocalDate?,
    onBirajKupnju: () -> Unit, onBirajDospijece: () -> Unit,
    greska: String?,
    onNatrag: () -> Unit,
    onSpremi: () -> Unit,
) {
    val osnovna = osnovnaValuta()
    Text(
        "Za zapise i obveznice bez javne cijene (npr. trezorski zapisi RH). Vrijednost svaki dan raste od uloženog prema iznosu pri dospijeću.",
        style = Tip.malo,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja("Oznaka")
        NeonPolje(oznaka, onOznaka, Modifier.fillMaxWidth(), placeholder = "npr. RHMF-T-722N")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja("Naziv (opcionalno)")
        NeonPolje(naziv, onNaziv, Modifier.fillMaxWidth(), placeholder = "npr. Trezorski zapis RH, 364 dana")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja("Uloženo (${osnovna.kod})")
        NeonPolje(ulozeno, onUlozeno, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja("Iznos pri dospijeću (${osnovna.kod}, nominala)")
        NeonPolje(nominal, onNominal, Modifier.fillMaxWidth(), placeholder = "0,00", uCentima = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GumbPolja("calendar", "Kupljeno ${HrDatum.puni(kupljeno)}", Modifier.weight(1f), onBirajKupnju)
        GumbPolja("calendar", dospijece?.let { "Dospijeće ${HrDatum.puni(it)}" } ?: "Dospijeće?", Modifier.weight(1f), onBirajDospijece)
    }
    // Pregled prinosa
    val ul = Novac.parsiraj(ulozeno)
    val nom = Novac.parsiraj(nominal)
    if (ul != null && nom != null && ul > 0 && dospijece != null && dospijece.isAfter(kupljeno)) {
        val dana = dospijece.toEpochDay() - kupljeno.toEpochDay()
        Portfelj.prinosGodisnji(ul / 100.0, nom / 100.0, kupljeno.toEpochDay(), dospijece.toEpochDay())?.let { p ->
            Text(
                "Prinos ${Valute.iznos((nom - ul) / 100.0, osnovna)} za $dana dana ≈ ${FormatTrzista.broj(p, 2)} % godišnje",
                color = Neon.Zelena, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
    greska?.let { Text(it, color = Neon.Crvena, fontSize = 12.sp) }
    PrimarniGumb("Dodaj zapis", onClick = onSpremi, ikona = "check")
    Text(
        "← Natrag na pretragu", color = Neon.Cijan, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onNatrag).padding(vertical = 6.dp),
    )
}


/**
 * Kamo ide novac od prodaje ili isplate: na odabrani račun (kao prijenos sa štednje, vraća stanje
 * ciklusa i ne broji se kao prihod) ili se samo bilježi (npr. novac ostaje kod brokera).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun OdredisteNovca(racuni: List<String>, odabran: String?, onOdabir: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OznakaPolja("Novac ide na")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            racuni.forEach { r -> hr.smartbudget.app.ui.NeonChip(r, r == odabran, { onOdabir(r) }, boja = Neon.Zelena) }
            hr.smartbudget.app.ui.NeonChip("Ne na račun", odabran == null, { onOdabir(null) }, boja = Neon.TekstSiv)
        }
        Text(
            if (odabran != null) "Upisuje se kao prijenos sa štednje na \"$odabran\": stanje računa raste, a iznos se ne broji kao prihod."
            else "Samo se bilježi (npr. novac ostaje kod brokera); stanje računa se ne mijenja.",
            style = Tip.oznaka.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
        )
    }
}
