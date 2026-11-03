package hr.smartbudget.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hr.smartbudget.app.MainActivity
import hr.smartbudget.app.R
import hr.smartbudget.app.domain.Novac

enum class VrstaObavijesti { PONAVLJAJUCI, MINUS, PROGNOZA, PODSJETNIK, TJEDNI }

/** Sadržaj obavijesti koji se prikazuje u aplikaciji kad korisnik dodirne obavijest. */
data class InfoObavijesti(
    val vrsta: VrstaObavijesti,
    val naslov: String,
    val tekst: String,
    val redovi: List<String> = emptyList(),
    val test: Boolean = false,
)

/** Obavijesti: naplaćeni ponavljajući unosi, minus, prognoza limita i dnevni podsjetnik. */
object Obavijesti {
    private const val KANAL = "smartbudget_obavijesti"
    private const val ID_PONAVLJAJUCI = 1001
    private const val ID_MINUS = 1002
    private const val ID_PODSJETNIK = 1003
    private const val ID_TJEDNI = 1004
    private const val ID_PROGNOZA = 2000
    private const val ID_TEST = 3000

    private const val EXTRA_VRSTA = "obavijest_vrsta"
    private const val EXTRA_NASLOV = "obavijest_naslov"
    private const val EXTRA_TEKST = "obavijest_tekst"
    private const val EXTRA_REDOVI = "obavijest_redovi"
    private const val EXTRA_TEST = "obavijest_test"

    /** Zadnja dodirnuta obavijest; MainActivity je prikazuje u prozoru i zatim briše. */
    val dodirnuta = mutableStateOf<InfoObavijesti?>(null)

    /** Čita obavijest iz namjere kojom je aplikacija otvorena (dodir na obavijest). */
    fun izNamjere(intent: Intent?) {
        val vrsta = intent?.getStringExtra(EXTRA_VRSTA)?.let { runCatching { VrstaObavijesti.valueOf(it) }.getOrNull() } ?: return
        dodirnuta.value = InfoObavijesti(
            vrsta,
            intent.getStringExtra(EXTRA_NASLOV).orEmpty(),
            intent.getStringExtra(EXTRA_TEKST).orEmpty(),
            intent.getStringArrayListExtra(EXTRA_REDOVI).orEmpty(),
            intent.getBooleanExtra(EXTRA_TEST, false),
        )
        intent.removeExtra(EXTRA_VRSTA) // da se prozor ne pojavi ponovno nakon okretanja zaslona
    }

    fun dopusteno(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun kanal(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Obavijesti o budžetu", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Ponavljajući unosi, stanje u minusu, prognoza limita i dnevni podsjetnik"
                },
            )
        }
    }

    /** Pošalji obavijest; dodir otvara aplikaciju s prozorom o toj obavijesti. */
    private fun posalji(context: Context, id: Int, info: InfoObavijesti) {
        if (!dopusteno(context)) return
        kanal(context)
        val namjera = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_VRSTA, info.vrsta.name)
            .putExtra(EXTRA_NASLOV, info.naslov)
            .putExtra(EXTRA_TEKST, info.tekst)
            .putStringArrayListExtra(EXTRA_REDOVI, ArrayList(info.redovi))
            .putExtra(EXTRA_TEST, info.test)
        val pending = PendingIntent.getActivity(context, id, namjera, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_obavijest)
            .setContentTitle(info.naslov)
            .setContentText(info.tekst)
            .setContentIntent(pending)
            .setAutoCancel(true)
        if (info.redovi.isNotEmpty()) {
            builder.setStyle(NotificationCompat.InboxStyle().also { s -> info.redovi.forEach { s.addLine(it) } }.setSummaryText(info.tekst))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(info.tekst))
        }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun infoPonavljajuci(stavke: List<Pair<String, Long>>, test: Boolean = false) = InfoObavijesti(
        VrstaObavijesti.PONAVLJAJUCI,
        (if (test) "[Test] " else "") + if (stavke.size == 1) "Naplaćen ponavljajući unos" else "Naplaćeno ${stavke.size} ponavljajućih unosa",
        "Ukupno ${Novac.predznak(stavke.sumOf { it.second })}",
        stavke.map { (naziv, centi) -> "$naziv  ${Novac.predznak(centi)}" },
        test,
    )

    private fun infoMinus(stanjeCenti: Long, test: Boolean = false) = InfoObavijesti(
        VrstaObavijesti.MINUS,
        (if (test) "[Test] " else "") + "Stanje računa je u minusu",
        "Trenutno stanje: ${Novac.stanje(stanjeCenti)}",
        test = test,
    )

    private fun infoPrognoza(naziv: String, prognozaCenti: Long, limitCenti: Long, test: Boolean = false) = InfoObavijesti(
        VrstaObavijesti.PROGNOZA,
        (if (test) "[Test] " else "") + "$naziv: limit će biti prekoračen",
        "Uz dosadašnji tempo do kraja ciklusa: ${Novac.eur(prognozaCenti)} od ${Novac.eur(limitCenti)}",
        test = test,
    )

    private fun infoPodsjetnik(test: Boolean = false) = InfoObavijesti(
        VrstaObavijesti.PODSJETNIK,
        (if (test) "[Test] " else "") + "Jeste li unijeli današnje troškove?",
        "Danas još nema unosa u SmartBudgetu.",
        test = test,
    )

    private fun infoTjedni(rashodi: Long, prihodi: Long, test: Boolean = false) = InfoObavijesti(
        VrstaObavijesti.TJEDNI,
        (if (test) "[Test] " else "") + "Tvoj tjedni pregled",
        "Zadnjih 7 dana: rashodi ${Novac.eur(rashodi)}, prihodi ${Novac.eur(prihodi)}. Dodirni za pregled.",
        test = test,
    )

    fun tjedni(context: Context, rashodi: Long, prihodi: Long) = posalji(context, ID_TJEDNI, infoTjedni(rashodi, prihodi))

    /** Stvoreni ponavljajući unosi: (naziv, predznačeni iznos u centima). */
    fun ponavljajuci(context: Context, stavke: List<Pair<String, Long>>) {
        if (stavke.isEmpty() || !AppPostavke.get(context).stanje.value.obavijestPonavljajuci) return
        posalji(context, ID_PONAVLJAJUCI, infoPonavljajuci(stavke))
    }

    /** Rano upozorenje: dosadašnji tempo vodi preko limita kategorije do kraja ciklusa. */
    fun prognoza(context: Context, kategorijaId: Long, naziv: String, prognozaCenti: Long, limitCenti: Long) =
        posalji(context, ID_PROGNOZA + kategorijaId.toInt(), infoPrognoza(naziv, prognozaCenti, limitCenti))

    fun podsjetnik(context: Context) = posalji(context, ID_PODSJETNIK, infoPodsjetnik())

    /** Šalje se samo pri prelasku iz plusa u minus. */
    fun provjeriMinus(context: Context, stanjeCenti: Long) {
        val postavke = AppPostavke.get(context)
        val negativno = stanjeCenti < 0
        if (negativno && !postavke.zadnjeStanjeNegativno && postavke.stanje.value.obavijestMinus) {
            posalji(context, ID_MINUS, infoMinus(stanjeCenti))
        }
        postavke.zadnjeStanjeNegativno = negativno
    }

    /** Testna obavijest s primjerom podataka, da korisnik vidi kako izgleda (šalje se i kad je vrsta isključena). */
    fun test(context: Context, vrsta: VrstaObavijesti) {
        val info = when (vrsta) {
            VrstaObavijesti.PONAVLJAJUCI -> infoPonavljajuci(listOf("Spotify" to -1_000L, "Stambeni kredit" to -48_000L), test = true)
            VrstaObavijesti.MINUS -> infoMinus(-7_510L, test = true)
            VrstaObavijesti.PROGNOZA -> infoPrognoza("Namirnice", 37_200L, 30_000L, test = true)
            VrstaObavijesti.PODSJETNIK -> infoPodsjetnik(test = true)
            VrstaObavijesti.TJEDNI -> infoTjedni(31_240L, 0L, test = true)
        }
        posalji(context, ID_TEST + vrsta.ordinal, info)
    }
}
