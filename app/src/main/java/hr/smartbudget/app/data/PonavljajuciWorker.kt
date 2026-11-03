package hr.smartbudget.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.smartbudget.app.widget.StanjeWidget
import java.util.concurrent.TimeUnit

/** Pozadinski posao: dospjeli ponavljajući unosi, osvježavanje widgeta i tjedna sigurnosna kopija. */
class PonavljajuciWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BudgetRepository.get(applicationContext).generirajPonavljajuce()
        StanjeWidget.osvjezi(applicationContext)
        BackupManager.get(applicationContext).tjednaAkoTreba()
        return Result.success()
    }

    companion object {
        fun zakazi(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ponavljajuci_unosi",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PonavljajuciWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
