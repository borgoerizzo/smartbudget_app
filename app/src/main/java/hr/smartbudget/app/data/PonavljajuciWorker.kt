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
        /** Dnevni podsjetnik u odabrano vrijeme (ili otkazivanje ako je isključen); vidi [Alarmi]. */
        fun zakaziPodsjetnik(context: Context) = Alarmi.zakaziPodsjetnik(context)

        /** Tjedni pregled na odabrani dan u tjednu i vrijeme (ili otkazivanje); vidi [Alarmi]. */
        fun zakaziTjedni(context: Context) = Alarmi.zakaziTjedni(context)

        fun zakazi(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ponavljajuci_unosi",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PonavljajuciWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
