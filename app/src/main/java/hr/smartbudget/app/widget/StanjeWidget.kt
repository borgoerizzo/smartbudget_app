package hr.smartbudget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import hr.smartbudget.app.BrziUnosActivity
import hr.smartbudget.app.MainActivity
import hr.smartbudget.app.R
import hr.smartbudget.app.data.AppPostavke
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.domain.HrDatum
import hr.smartbudget.app.domain.Novac
import kotlinx.coroutines.launch

/** Widget: stanje računa, ulaz i izlaz u aktivnom ciklusu i gumbi za brzi unos. */
class StanjeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val rezultat = goAsync()
        BudgetRepository.get(context).opseg.launch {
            try {
                BudgetRepository.get(context).generirajPonavljajuce()
                azuriraj(context, manager, ids)
            } finally { rezultat.finish() }
        }
    }

    companion object {
        /** Traži od pokretača da doda widget na početni zaslon; false ako pokretač to ne podržava. */
        fun zatraziDodavanje(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, StanjeWidget::class.java), null, null)
        }

        /** Osvježi sve postavljene widgete (poziva se nakon svake promjene podataka). */
        suspend fun osvjezi(context: Context) {
            StanjeMaliWidget.osvjezi(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StanjeWidget::class.java))
            if (ids.isNotEmpty()) azuriraj(context, manager, ids)
        }

        private suspend fun azuriraj(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = BudgetRepository.get(context).sazetak()
            val privatnost = AppPostavke.get(context).stanje.value
            val v = RemoteViews(context.packageName, R.layout.widget_stanje).apply {
                setTextViewText(R.id.widget_stanje, if (privatnost.sakrijStanje) AppPostavke.SKRIVENO else Novac.stanje(s.stanje))
                setTextColor(R.id.widget_stanje, if (s.stanje >= 0) 0xFF00F5A0.toInt() else 0xFFFF5376.toInt())
                setTextViewText(R.id.widget_ulaz, if (privatnost.sakrijPrihod) "+" + AppPostavke.SKRIVENO else Novac.predznak(s.ciklus.prihodi))
                setTextViewText(R.id.widget_izlaz, Novac.predznak(-s.ciklus.troskovi))
                setTextViewText(R.id.widget_ciklus, HrDatum.raspon(s.raspon))
                setOnClickPendingIntent(R.id.widget_gumb_prihod, brziUnos(context, TipTransakcije.PRIHOD))
                setOnClickPendingIntent(R.id.widget_gumb_trosak, brziUnos(context, TipTransakcije.TROSAK))
                setOnClickPendingIntent(
                    R.id.widget_korijen,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(ids, v)
        }

        private fun brziUnos(context: Context, tip: TipTransakcije): PendingIntent = PendingIntent.getActivity(
            context,
            1 + tip.ordinal,
            Intent(context, BrziUnosActivity::class.java)
                .putExtra(BrziUnosActivity.EXTRA_TIP, tip.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
