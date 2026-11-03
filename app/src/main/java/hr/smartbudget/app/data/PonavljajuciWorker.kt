package hr.smartbudget.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.smartbudget.app.widget.StanjeWidget
import java.util.concurrent.TimeUnit

/** Tjedni pregled: obavijest s rashodima i prihodima zadnjih 7 dana. */
class TjedniWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AppPostavke.get(applicationContext).stanje.value.tjedni) {
            val s = BudgetRepository.get(applicationContext).tjedniSazetak()
            Obavijesti.tjedni(applicationContext, s.zbroj.troskovi, s.zbroj.prihodi)
        }
        return Result.success()
    }
}

/** Dnevni podsjetnik: obavijest samo ako danas još nema ručnog unosa. */
class PodsjetnikWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AppPostavke.get(applicationContext).stanje.value.podsjetnik &&
            !BudgetRepository.get(applicationContext).imaUnosaDanas()
        ) Obavijesti.podsjetnik(applicationContext)
        return Result.success()
    }
}

/** Pozadinski posao: dospjeli ponavljajući unosi, osvježavanje widgeta i tjedna sigurnosna kopija. */
class PonavljajuciWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BudgetRepository.get(applicationContext).generirajPonavljajuce()
        StanjeWidget.osvjezi(applicationContext)
        BackupManager.get(applicationContext).tjednaAkoTreba()
        runCatching { BudgetRepository.get(applicationContext).provjeriPrognoze() }
        return Result.success()
    }

    companion object {
        /** Dnevni podsjetnik u odabrano vrijeme (ili otkazivanje ako je isključen). */
        fun zakaziPodsjetnik(context: Context) {
            val p = AppPostavke.get(context).stanje.value
            val wm = WorkManager.getInstance(context)
            if (!p.podsjetnik) { wm.cancelUniqueWork("podsjetnik"); return }
            val sada = java.time.LocalDateTime.now()
            var sljedeci = sada.toLocalDate().atTime(p.podsjetnikMinuta / 60, p.podsjetnikMinuta % 60)
            if (!sljedeci.isAfter(sada)) sljedeci = sljedeci.plusDays(1)
            val odgoda = java.time.Duration.between(sada, sljedeci).toMinutes()
            wm.enqueueUniquePeriodicWork(
                "podsjetnik",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PodsjetnikWorker>(1, TimeUnit.DAYS).setInitialDelay(odgoda, TimeUnit.MINUTES).build(),
            )
        }

        /** Tjedni pregled na odabrani dan u tjednu i vrijeme (ili otkazivanje). */
        fun zakaziTjedni(context: Context) {
            val p = AppPostavke.get(context).stanje.value
            val wm = WorkManager.getInstance(context)
            if (!p.tjedni) { wm.cancelUniqueWork("tjedni"); return }
            val sada = java.time.LocalDateTime.now()
            var sljedeci = sada.toLocalDate().atTime(p.tjedniMinuta / 60, p.tjedniMinuta % 60)
            while (sljedeci.dayOfWeek.value != p.tjedniDan || !sljedeci.isAfter(sada)) sljedeci = sljedeci.plusDays(1)
            val odgoda = java.time.Duration.between(sada, sljedeci).toMinutes()
            wm.enqueueUniquePeriodicWork(
                "tjedni",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TjedniWorker>(7, TimeUnit.DAYS).setInitialDelay(odgoda, TimeUnit.MINUTES).build(),
            )
        }

        fun zakazi(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ponavljajuci_unosi",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PonavljajuciWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
