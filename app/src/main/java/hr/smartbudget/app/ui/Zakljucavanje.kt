package hr.smartbudget.app.ui

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.ui.theme.Neon
import hr.smartbudget.app.ui.theme.Tip

/** Stanje zaključavanja aplikacije (zajedničko za glavni zaslon i brzi unos s widgeta). */
object Zakljucavanje {
    val zakljucano = mutableStateOf(false)
    private var uPozadiniOd = 0L
    private var prviPut = true

    /** Poziva se iz onStart: zaključa ako je uključeno i aplikacija je bila dovoljno dugo u pozadini. */
    fun naPokretanju(context: Context) {
        val p = AppPostavke.get(context).stanje.value
        val proteklo = (SystemClock.elapsedRealtime() - uPozadiniOd) / 1000
        // Otkriveni iznosi štednje se ponovno skrivaju nakon odgode u pozadini.
        if (prviPut || proteklo >= p.odgodaSekundi) Dijelovi.stednjaOtkrivena.value = false
        if (!p.zakljucavanje || !p.imaPin) { zakljucano.value = false; prviPut = false; return }
        if (prviPut || proteklo >= p.odgodaSekundi) {
            // Cijela aplikacija, ili samo jedan dio (ciklus / štednja) koji traži otključavanje kad se otvori.
            if (p.opsegZakljucavanja == Dijelovi.SVE) zakljucano.value = true
            else { zakljucano.value = false; Dijelovi.otkljucano.value = emptySet() }
        }
        prviPut = false
    }

    fun uPozadinu() { uPozadiniOd = SystemClock.elapsedRealtime() }

    fun biometrijaDostupna(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            context.getSystemService(BiometricManager::class.java)
                ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            @Suppress("DEPRECATION")
            context.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        else -> false
    }

    /** Otisak prsta ili lice; "Koristi PIN" zatvara dijalog i ostavlja PIN tipkovnicu. */
    fun biometrija(activity: Activity, onUspjeh: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val izvrsitelj = activity.mainExecutor
        val upit = BiometricPrompt.Builder(activity)
            .setTitle("Otključaj SmartBudget")
            .setSubtitle("Otisak prsta ili lice")
            .setNegativeButton("Koristi PIN", izvrsitelj) { _, _ -> }
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK) }
            .build()
        upit.authenticate(
            CancellationSignal(), izvrsitelj,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onUspjeh()
            },
        )
    }
}

private tailrec fun Context.aktivnost(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.aktivnost()
    else -> null
}

/** Zaslon zaključavanja preko cijele aplikacije: biometrija (ako je uključena), inače/uz to PIN. */
@Composable
fun EkranZakljucavanja() {
    val ctx = LocalContext.current
    val postavke = remember { AppPostavke.get(ctx) }
    val p = postavke.stanje.value
    val bio = p.biometrija && Zakljucavanje.biometrijaDostupna(ctx)
    var pin by remember { mutableStateOf("") }
    var greska by remember { mutableStateOf(false) }
    val otkljucaj = { Zakljucavanje.zakljucano.value = false }

    LaunchedEffect(Unit) { if (bio) ctx.aktivnost()?.let { Zakljucavanje.biometrija(it, otkljucaj) } }

    // Zaboravljen PIN: potvrda zaključavanjem zaslona mobitela isključuje zaključavanje aplikacije.
    var bezZastite by remember { mutableStateOf(false) }
    val oporavak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { postavke.ukloniPin(); otkljucaj() }
    }
    val zaboravljen = {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val namjera = if (km?.isDeviceSecure == true) km.createConfirmDeviceCredentialIntent("Oporavak PIN-a", "Potvrdite zaključavanjem zaslona mobitela") else null
        if (namjera != null) oporavak.launch(namjera) else bezZastite = true
    }
    if (bezZastite) {
        AlertDialog(
            onDismissRequest = { bezZastite = false },
            containerColor = Neon.Sheet,
            title = { Text("Oporavak nije moguć", color = Neon.Tekst) },
            text = {
                Text(
                    "Mobitel nema zaključavanje zaslona pa se PIN ne može resetirati potvrdom. " +
                        "Preostaje brisanje podataka aplikacije i vraćanje iz sigurnosne kopije.",
                    color = Neon.TekstSvijetliSiv,
                )
            },
            confirmButton = { TextButton({ bezZastite = false }) { Text("U redu", color = Neon.Zelena) } },
        )
    }

    Box(Modifier.fillMaxSize().background(Neon.Pozadina).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PlocicaIkone(Ikone.ui("lock"), Neon.Zelena, 64.dp, sjaj = true)
            Text("SmartBudget je zaključan", style = Tip.naslov)
            Text(if (greska) "Pogrešan PIN, pokušajte ponovno" else "Unesite PIN", color = if (greska) Neon.Crvena else Neon.TekstSiv, fontSize = 14.sp)
            TockePina(pin.length, greska)
            PinTipkovnica(
                onZnamenka = { z ->
                    if (pin.length < 6) {
                        pin += z; greska = false
                        // PIN se provjerava od 4 znamenke nadalje; točan otključava odmah.
                        if (pin.length >= 4 && postavke.provjeriPin(pin)) otkljucaj()
                        else if (pin.length == 6) { greska = true; pin = "" }
                    }
                },
                onBrisi = { pin = pin.dropLast(1) },
                lijevo = if (bio) ({ ctx.aktivnost()?.let { Zakljucavanje.biometrija(it, otkljucaj) } }) else null,
            )
            TextButton(zaboravljen) { Text("Zaboravljen PIN?", color = Neon.TekstSiv) }
        }
    }
}

@Composable
fun TockePina(broj: Int, greska: Boolean = false, ukupno: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(ukupno) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (i < broj) (if (greska) Neon.Crvena else Neon.Zelena) else Color(0x1FFFFFFF)),
            )
        }
    }
}

/** Numerička tipkovnica 3×4; [lijevo] je opcionalni gumb biometrije u donjem lijevom kutu. */
@Composable
fun PinTipkovnica(onZnamenka: (Char) -> Unit, onBrisi: () -> Unit, lijevo: (() -> Unit)? = null) {
    val redovi = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        redovi.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { r.forEach { z -> TipkaPina(z.toString()) { onZnamenka(z) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (lijevo != null) TipkaPina(ikona = "fingerprint", opis = "Otključaj biometrijom", onClick = lijevo) else Spacer(Modifier.size(72.dp))
            TipkaPina("0") { onZnamenka('0') }
            TipkaPina(ikona = "backspace", opis = "Obriši znamenku", onClick = onBrisi)
        }
    }
}

@Composable
private fun TipkaPina(tekst: String? = null, ikona: String? = null, opis: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Neon.RubSlab, CircleShape)
            .clickable(role = Role.Button, onClickLabel = opis ?: tekst, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tekst != null) Text(tekst, color = Neon.Tekst, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (ikona != null) Icon(Ikone.ui(ikona), opis, Modifier.size(26.dp), tint = Neon.Cijan)
    }
}

/** Postavljanje novog PIN-a: upis 4–6 znamenki, pa potvrda. */
@Composable
fun PostavljanjePinaDijalog(onGotovo: (String) -> Unit, onOdustani: () -> Unit) {
    var prvi by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var poruka by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onOdustani, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Neon.Pozadina).systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (prvi == null) "Novi PIN (4–6 znamenki)" else "Ponovite PIN", style = Tip.naslov)
                Text(poruka ?: " ", color = Neon.Crvena, fontSize = 13.sp, textAlign = TextAlign.Center)
                TockePina(pin.length)
                PinTipkovnica(onZnamenka = { if (pin.length < 6) { pin += it; poruka = null } }, onBrisi = { pin = pin.dropLast(1) })
                Spacer(Modifier.height(4.dp))
                Row(Modifier.width(260.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TonalniGumb("Odustani", onOdustani, Modifier.weight(1f), boja = Neon.TekstSiv)
                    TonalniGumb(
                        if (prvi == null) "Dalje" else "Spremi",
                        {
                            when {
                                pin.length < 4 -> poruka = "PIN mora imati barem 4 znamenke."
                                prvi == null -> { prvi = pin; pin = "" }
                                prvi == pin -> onGotovo(pin)
                                else -> { poruka = "PIN-ovi se ne podudaraju. Ispočetka."; prvi = null; pin = "" }
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
