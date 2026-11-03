package hr.smartbudget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import hr.smartbudget.app.MainActivity
import hr.smartbudget.app.R
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.domain.Novac
import kotlinx.coroutines.launch

/** Mali widget 2×1: samo stanje računa; dodir otvara aplikaciju. */
class StanjeMaliWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val rezultat = goAsync()
        BudgetRepository.get(context).opseg.launch {
            try { azuriraj(context, manager, ids) } finally { rezultat.finish() }
        }
    }

    companion object {
        suspend fun osvjezi(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StanjeMaliWidget::class.java))
            if (ids.isNotEmpty()) azuriraj(context, manager, ids)
        }

        private suspend fun azuriraj(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val stanje = BudgetRepository.get(context).sazetak().stanje
            val sakrij = AppPostavke.get(context).stanje.value.sakrijStanje
            val v = RemoteViews(context.packageName, R.layout.widget_mali).apply {
                setTextViewText(R.id.mali_stanje, if (sakrij) AppPostavke.SKRIVENO else Novac.stanje(stanje))
                setTextColor(R.id.mali_stanje, if (stanje >= 0) 0xFF00F5A0.toInt() else 0xFFFF5376.toInt())
                setOnClickPendingIntent(
                    R.id.mali_korijen,
                    PendingIntent.getActivity(
                        context, 10,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(ids, v)
        }
    }
}
