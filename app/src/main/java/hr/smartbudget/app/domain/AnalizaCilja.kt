package hr.smartbudget.app.domain

import hr.smartbudget.app.data.CiljStednje
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class StatusCilja { DOSEGNUT, ISPRED_PLANA, NA_PUTU, KASNI, ISTEKAO, BEZ_ROKA }

/**
 * Izračuni za cilj štednje: odbrojavanje do roka, koliko treba odvajati dnevno/tjedno/mjesečno,
 * usporedba s planom (koliko je trebalo biti ušteđeno do danas) i procjena kad će cilj biti dosegnut.
 */
data class AnalizaCilja(
    val udio: Float,
    val ostalo: Long,
    val danaDoRoka: Long?,
    val dnevno: Long?,
    val tjedno: Long?,
    val mjesecno: Long?,
    /** Koliko je po planu (ravnomjerno od postavljanja do roka) trebalo biti ušteđeno do danas. */
    val ocekivanoDoSad: Long?,
    val status: StatusCilja,
    /** Prosječna štednja mjesečno od postavljanja cilja (null dok nije prošlo barem 14 dana). */
    val tempoMjesecno: Long?,
    /** Kad bi cilj bio dosegnut nastavi li se dosadašnjim tempom. */
    val procjena: LocalDate?,
    /** Sljedeći prag (25, 50, 75, 100 %) i koliko još do njega. */
    val sljedeciPrag: Int?,
    val doPraga: Long,
)

private fun dijeliGore(a: Long, b: Long): Long = if (b <= 0) a else (a + b - 1) / b

fun analizirajCilj(c: CiljStednje, danas: LocalDate = LocalDate.now()): AnalizaCilja {
    val ostalo = (c.ciljCenti - c.ustedjenoCenti).coerceAtLeast(0)
    val udio = if (c.ciljCenti > 0) (c.ustedjenoCenti.toFloat() / c.ciljCenti).coerceIn(0f, 1f) else 0f
    val rok = c.rokEpochDan?.let { LocalDate.ofEpochDay(it) }
    val kreiran = c.kreiranEpochDan?.let { LocalDate.ofEpochDay(it) }

    val danaDoRoka = rok?.let { ChronoUnit.DAYS.between(danas, it) }
    val aktivnoDana = danaDoRoka?.takeIf { it > 0 }
    val dnevno = aktivnoDana?.let { if (ostalo > 0) dijeliGore(ostalo, it) else 0L }
    val tjedno = aktivnoDana?.let { if (ostalo > 0) dijeliGore(ostalo * 7, it).coerceAtMost(ostalo) else 0L }
    val mjesecno = aktivnoDana?.let { if (ostalo > 0) ceil(ostalo * 30.44 / it).toLong().coerceAtMost(ostalo) else 0L }

    val ocekivano = if (rok != null && kreiran != null && rok.isAfter(kreiran)) {
        val ukupno = ChronoUnit.DAYS.between(kreiran, rok).toDouble()
        val proteklo = ChronoUnit.DAYS.between(kreiran, danas).coerceIn(0, ukupno.toLong()).toDouble()
        (c.ciljCenti * proteklo / ukupno).toLong()
    } else null

    val status = when {
        ostalo == 0L -> StatusCilja.DOSEGNUT
        rok == null -> StatusCilja.BEZ_ROKA
        danaDoRoka!! < 0 -> StatusCilja.ISTEKAO
        ocekivano == null -> StatusCilja.NA_PUTU
        // Tolerancija 5 % cilja da sitne razlike ne mijenjaju status
        c.ustedjenoCenti >= ocekivano + c.ciljCenti / 20 -> StatusCilja.ISPRED_PLANA
        c.ustedjenoCenti >= ocekivano - c.ciljCenti / 20 -> StatusCilja.NA_PUTU
        else -> StatusCilja.KASNI
    }

    val danaOdPostavljanja = kreiran?.let { ChronoUnit.DAYS.between(it, danas) } ?: 0
    val tempoDnevno = if (danaOdPostavljanja >= 14 && c.ustedjenoCenti > 0) c.ustedjenoCenti.toDouble() / danaOdPostavljanja else null
    val tempoMjesecno = tempoDnevno?.let { (it * 30.44).toLong() }
    val procjena = if (ostalo > 0 && tempoDnevno != null) danas.plusDays(ceil(ostalo / tempoDnevno).toLong()) else null

    val prag = listOf(25, 50, 75, 100).firstOrNull { c.ustedjenoCenti < c.ciljCenti * it / 100 }
    val doPraga = prag?.let { c.ciljCenti * it / 100 - c.ustedjenoCenti } ?: 0L

    return AnalizaCilja(udio, ostalo, danaDoRoka, dnevno, tjedno, mjesecno, ocekivano, status, tempoMjesecno, procjena, prag, doPraga)
}
