package hr.smartbudget.app

import android.app.Application
import hr.smartbudget.app.data.AppPostavke

/** Učitava postavke (i oznaku valute) prije bilo kojeg dijela aplikacije: zaslona, widgeta ili pozadinskog posla. */
class SmartBudgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPostavke.get(this)
    }
}
