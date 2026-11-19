package hr.smartbudget.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import hr.smartbudget.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Verzija objavljena na GitHubu (Releases), s popisom izmjena i APK datotekom. */
data class NovaVerzija(
    /** Npr. "3.001". */
    val naziv: String,
    /** Kao versionCode: 3.001 → 3001. */
    val kod: Int,
    val izmjene: String,
    val apkUrl: String,
    val velicina: Long,
)

/**
 * Provjera i preuzimanje novih verzija s GitHub Releases javnog repozitorija.
 *
 * Pravilo verzija: +0,001 za manje izmjene, +0,1 za veće. Ažuriranje je obavezno kad nova verzija
 * mijenja prvu decimalu (npr. 3.0xx → 3.1xx); razlika samo u tisućinkama (3.000 → 3.004) se može odbiti.
 */
object Azuriranja {
    const val REPO = "borgoerizzo/smartbudget_app"
    private const val ZADNJA = "https://api.github.com/repos/$REPO/releases/latest"

    /** "3.001" ili "v3.001" → 3001 (isto kao versionCode u build.gradle.kts); null ako oznaka nije u tom obliku. */
    fun kodVerzije(naziv: String): Int? {
        val dijelovi = naziv.trim().removePrefix("v").removePrefix("V").split('.')
        if (dijelovi.size != 2 || dijelovi[1].length != 3) return null
        val a = dijelovi[0].toIntOrNull() ?: return null
        val b = dijelovi[1].toIntOrNull() ?: return null
        return a * 1000 + b
    }

    /** Obavezno kad se razlikuje cijeli broj ili prva decimala (korak 0,1). */
    fun obavezno(instalirana: Int, nova: Int): Boolean = nova / 100 > instalirana / 100

    fun obavezno(n: NovaVerzija): Boolean = obavezno(BuildConfig.VERSION_CODE, n.kod)

    /** Odgovor GitHub API-ja (releases/latest) → verzija; null ako nema APK-a ili oznaka nije verzija. */
    fun parsiraj(json: String): NovaVerzija? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val oznaka = o.optString("tag_name")
        val kod = kodVerzije(oznaka) ?: return null
        val prilozi = o.optJSONArray("assets") ?: return null
        val apk = (0 until prilozi.length()).map { prilozi.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        return NovaVerzija(
            naziv = oznaka.removePrefix("v").removePrefix("V"),
            kod = kod,
            izmjene = o.optString("body").replace("\r\n", "\n").trim(),
            apkUrl = apk.getString("browser_download_url"),
            velicina = apk.optLong("size"),
        )
    }

    /** Najnovija objavljena verzija ako je novija od instalirane, inače null. Baca iznimku ako mreža ne radi. */
    suspend fun provjeri(): NovaVerzija? = withContext(Dispatchers.IO) {
        val c = URL(ZADNJA).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "SmartBudget-Android")
        try {
            when (c.responseCode) {
                404 -> null // još nema nijednog izdanja
                in 200..299 -> parsiraj(c.inputStream.bufferedReader().use { it.readText() })
                    ?.takeIf { it.kod > BuildConfig.VERSION_CODE }
                else -> error("HTTP ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    /** Preuzima APK u privatnu mapu aplikacije; [napredak] dobiva udio 0..1. */
    suspend fun preuzmi(ctx: Context, n: NovaVerzija, napredak: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "azuriranje").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "SmartBudget-${n.naziv}.apk")
        val c = URL(n.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "SmartBudget-Android")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val ukupno = c.contentLengthLong.takeIf { it > 0 } ?: n.velicina
            c.inputStream.use { ulaz ->
                f.outputStream().use { izlaz ->
                    val buf = ByteArray(64 * 1024)
                    var procitano = 0L
                    while (true) {
                        val k = ulaz.read(buf)
                        if (k < 0) break
                        izlaz.write(buf, 0, k)
                        procitano += k
                        if (ukupno > 0) napredak((procitano.toFloat() / ukupno).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        f
    }

    /** Smije li aplikacija pokrenuti instalaciju (Android 8+ traži dopuštenje "Instaliraj nepoznate aplikacije"). */
    fun smijeInstalirati(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** Otvara postavke dopuštenja za instaliranje iz ove aplikacije. */
    fun otvoriDopustenje(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Pokreće sistemsku instalaciju preuzetog APK-a (Android sam provjerava potpis i verziju). */
    fun instaliraj(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // Odbijena neobavezna verzija se pri pokretanju više ne nudi sama (ručna provjera je i dalje pokazuje).
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("azuriranja", Context.MODE_PRIVATE)
    fun odbijena(ctx: Context, kod: Int) = prefs(ctx).getInt("odbijena", 0) == kod
    fun odbij(ctx: Context, kod: Int) = prefs(ctx).edit().putInt("odbijena", kod).apply()
}
