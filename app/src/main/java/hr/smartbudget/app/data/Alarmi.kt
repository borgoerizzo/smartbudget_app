package hr.smartbudget.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Dnevni podsjetnik i tjedni pregled preko AlarmManagera, da stignu u zadano vrijeme.
 * (Periodični WorkManager posao Android odgađa po volji, pa je podsjetnik znao stići usred noći.)
 * Bez dopuštenja za točne alarme koristi se alarm koji radi i u štednom načinu, uz moguće kašnjenje do sat vremena.
 */
object Alarmi {
    const val AKCIJA_PODSJETNIK = "hr.smartbudget.app.PODSJETNIK"
    const val AKCIJA_TJEDNI = "hr.smartbudget.app.TJEDNI"

    fun mozeTocno(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Ponovno postavlja oba alarma (pokretanje aplikacije, ponovno pokretanje uređaja, promjena vremena). */
    fun obnovi(context: Context) {
        // Stari periodični poslovi iz ranijih verzija
        WorkManager.getInstance(context).apply { cancelUniqueWork("podsjetnik"); cancelUniqueWork("tjedni") }
        zakaziPodsjetnik(context)
        zakaziTjedni(context)
    }

    fun zakaziPodsjetnik(context: Context) {
        val p = AppPostavke.get(context).stanje.value
        val sada = LocalDateTime.now()
        var sljedeci = sada.toLocalDate().atTime(p.podsjetnikMinuta / 60, p.podsjetnikMinuta % 60)
        if (!sljedeci.isAfter(sada)) sljedeci = sljedeci.plusDays(1)
        postavi(context, AKCIJA_PODSJETNIK, if (p.podsjetnik) sljedeci else null)
    }

    fun zakaziTjedni(context: Context) {
        val p = AppPostavke.get(context).stanje.value
        val sada = LocalDateTime.now()
        var sljedeci = sada.toLocalDate().atTime(p.tjedniMinuta / 60, p.tjedniMinuta % 60)
        while (sljedeci.dayOfWeek.value != p.tjedniDan || !sljedeci.isAfter(sada)) sljedeci = sljedeci.plusDays(1)
        postavi(context, AKCIJA_TJEDNI, if (p.tjedni) sljedeci else null)
    }

    private fun postavi(context: Context, akcija: String, kada: LocalDateTime?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, akcija.hashCode(),
            Intent(context, AlarmPrijemnik::class.java).setAction(akcija),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (kada == null) return
        val ms = kada.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (mozeTocno(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }
}

class AlarmPrijemnik : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            // Sljedeći alarm se postavlja odmah, da lanac ne pukne ni ako obavijest ne uspije.
            Alarmi.AKCIJA_PODSJETNIK -> {
                Alarmi.zakaziPodsjetnik(ctx)
                radi {
                    if (AppPostavke.get(ctx).stanje.value.podsjetnik && !BudgetRepository.get(ctx).imaUnosaDanas()) Obavijesti.podsjetnik(ctx)
                }
            }
            Alarmi.AKCIJA_TJEDNI -> {
                Alarmi.zakaziTjedni(ctx)
                radi {
                    if (AppPostavke.get(ctx).stanje.value.tjedni) {
                        val s = BudgetRepository.get(ctx).tjedniSazetak()
                        Obavijesti.tjedni(ctx, s.zbroj.troskovi, s.zbroj.prihodi)
                    }
                }
            }
            // Uređaj ponovno pokrenut, promijenjeno vrijeme/zona, nova verzija ili dopuštenje za točne alarme
            else -> Alarmi.obnovi(ctx)
        }
    }

    private fun radi(posao: suspend () -> Unit) {
        val rezultat = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runCatching { posao() } } finally { rezultat.finish() }
        }
    }
}
