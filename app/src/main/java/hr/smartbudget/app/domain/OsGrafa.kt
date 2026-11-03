package hr.smartbudget.app.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Raspon osi Y (u centima) s oznakama; 0 je uvijek uključena. */
data class OsGrafa(val min: Double, val max: Double, val oznake: List<Double>)

object Os {
    /** Udio visine grafa koji minus dobiva barem, da se i mali minus jasno vidi. */
    private const val MIN_UDIO_MINUSA = 0.15

    /** "Lijep" korak (1, 2, 2.5, 5 × 10ⁿ) za zaokruživanje granica. */
    private fun lijepo(v: Double): Double {
        if (v <= 0) return 0.0
        val red = 10.0.pow(floor(log10(v)))
        val m = v / red
        val k = when {
            m <= 1 -> 1.0
            m <= 2 -> 2.0
            m <= 2.5 -> 2.5
            m <= 5 -> 5.0
            else -> 10.0
        }
        return k * red
    }

    /**
     * Gornja granica ~5 % iznad najveće vrijednosti, donja prema stvarnom minusu (+25 %),
     * ali minus zauzima barem [MIN_UDIO_MINUSA] visine. Oznake: vrh, polovica, 0 i dno.
     */
    fun izracunaj(najmanja: Double, najveca: Double): OsGrafa {
        val vrh = lijepo(max(najveca, 0.0) * 1.05).coerceAtLeast(10_000.0) // barem 100 €
        if (najmanja >= 0) return OsGrafa(0.0, vrh, listOf(0.0, vrh / 2, vrh))
        var dno = abs(najmanja) * 1.25
        val minimalno = vrh * MIN_UDIO_MINUSA / (1 - MIN_UDIO_MINUSA)
        if (dno < minimalno) dno = minimalno
        // Zaokruži dno na korak od 10 %, 20 % ili 25 % vrha radi čitljivih oznaka.
        val korak = lijepo(dno / 2).coerceAtLeast(100.0)
        dno = ceil(dno / korak) * korak
        return OsGrafa(-dno, vrh, listOf(-dno, 0.0, vrh / 2, vrh))
    }
}
