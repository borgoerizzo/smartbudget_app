package hr.smartbudget.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Fotografije računa: spremaju se smanjene (najviše 1600 px) u internu memoriju aplikacije. */
object Fotografije {
    private const val MAKS_PX = 1600

    private fun mapa(context: Context) = File(context.filesDir, "racuni").apply { mkdirs() }

    /** Privremena datoteka i URI za kameru (TakePicture). */
    fun zaKameru(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "kamera").apply { mkdirs() }
        val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f) to f
    }

    /** Kopira sliku (iz galerije ili kamere), smanji je i vrati putanju spremljene datoteke. */
    fun spremi(context: Context, uri: Uri): String? = runCatching {
        val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, granice) }
        var uzorak = 1
        while (granice.outWidth / uzorak > MAKS_PX * 2 || granice.outHeight / uzorak > MAKS_PX * 2) uzorak *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = uzorak })
        } ?: return null
        val omjer = minOf(1f, MAKS_PX.toFloat() / maxOf(bmp.width, bmp.height))
        val manja = if (omjer < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * omjer).toInt(), (bmp.height * omjer).toInt(), true) else bmp
        val f = File(mapa(context), "racun_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { manja.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        f.absolutePath
    }.getOrNull()

    /** Umanjena slika za prikaz. */
    fun ucitaj(putanja: String, maksPx: Int): Bitmap? = runCatching {
        val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(putanja, granice)
        var uzorak = 1
        while (granice.outWidth / uzorak > maksPx * 2 || granice.outHeight / uzorak > maksPx * 2) uzorak *= 2
        BitmapFactory.decodeFile(putanja, BitmapFactory.Options().apply { inSampleSize = uzorak })
    }.getOrNull()

    fun obrisi(putanja: String?) { putanja?.let { runCatching { File(it).delete() } } }
}
