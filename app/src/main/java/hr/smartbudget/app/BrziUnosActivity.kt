package hr.smartbudget.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import hr.smartbudget.app.data.BudgetRepository
import hr.smartbudget.app.data.TipTransakcije
import hr.smartbudget.app.ui.screens.TransakcijaSheet
import hr.smartbudget.app.ui.theme.SmartBudgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Brzi unos prihoda ili troška s widgeta: samo sheet za unos preko početnog zaslona. */
class BrziUnosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val tip = intent.getStringExtra(EXTRA_TIP)?.let { runCatching { TipTransakcije.valueOf(it) }.getOrNull() }
            ?: TipTransakcije.TROSAK
        val repo = BudgetRepository.get(this)
        val app = applicationContext

        setContent {
            SmartBudgetTheme {
                val kategorije by repo.db.kategorije().promatrajSve().collectAsState(initial = emptyList())
                TransakcijaSheet(
                    postojeca = null,
                    kategorije = kategorije,
                    pocetniTip = tip,
                    fokusNaIznos = true,
                    onSpremi = { t ->
                        repo.opseg.launch {
                            val poruke = repo.spremiTransakciju(t)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(app, poruke.joinToString("\n"), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onObrisi = {},
                    onZatvori = { finish() },
                    onSpremiPonavljajuci = { p ->
                        repo.opseg.launch {
                            val novih = repo.spremiPonavljajuci(p)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    app,
                                    "Ponavljajući unos dodan" + if (novih > 0) " · stvoreno unosa: $novih" else "",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TIP = "tip"
    }
}
