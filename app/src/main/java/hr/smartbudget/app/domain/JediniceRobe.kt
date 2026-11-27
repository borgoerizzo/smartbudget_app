package hr.smartbudget.app.domain

/**
 * Preračun cijene robe s burzovne jedinice (unca, funta, barel, bušel, MMBtu) na jedinice
 * kakve se koriste u Hrvatskoj (gram, kilogram, litra, kWh).
 */
object JediniceRobe {
    /** [izvorna] = opis burzovne jedinice; [jedinice] = (naziv, koliko izvornih jedinica stane u tu jedinicu). */
    data class Pretvorba(val izvorna: String, val jedinice: List<Pair<String, Double>>, val kratica: String)

    private const val UNCA_G = 31.1034768 // trojska unca
    private const val FUNTA_KG = 0.45359237
    private const val BAREL_L = 158.987294928
    private const val MMBTU_KWH = 293.07107
    private const val BUSEL_PSENICE_KG = 27.2155422 // 60 lb

    private val plemeniti = Pretvorba("trojska unca (31,1 g)", listOf("1 g" to 1 / UNCA_G, "1 kg" to 1000 / UNCA_G), "oz")

    private val tablica = mapOf(
        "GC=F" to plemeniti, "SI=F" to plemeniti, "PL=F" to plemeniti, "PA=F" to plemeniti,
        "HG=F" to Pretvorba("funta (0,454 kg)", listOf("1 kg" to 1 / FUNTA_KG, "1 t" to 1000 / FUNTA_KG), "lb"),
        "CL=F" to Pretvorba("barel (159 l)", listOf("1 l" to 1 / BAREL_L), "bbl"),
        "BZ=F" to Pretvorba("barel (159 l)", listOf("1 l" to 1 / BAREL_L), "bbl"),
        "NG=F" to Pretvorba("MMBtu (293 kWh)", listOf("1 kWh" to 1 / MMBTU_KWH, "1 MWh" to 1000 / MMBTU_KWH), "MMBtu"),
        "ZW=F" to Pretvorba("bušel (27,2 kg)", listOf("1 kg" to 1 / BUSEL_PSENICE_KG, "1 t" to 1000 / BUSEL_PSENICE_KG), "bu"),
        "KC=F" to Pretvorba("funta (0,454 kg)", listOf("1 kg" to 1 / FUNTA_KG), "lb"),
    )

    /** "1 g" → "po gramu" itd. */
    fun po(jedinica: String): String = when (jedinica.removePrefix("1 ")) {
        "g" -> "po gramu"; "kg" -> "po kilogramu"; "t" -> "po toni"; "l" -> "po litri"
        else -> "po " + jedinica.removePrefix("1 ")
    }

    fun za(simbol: String): Pretvorba? = tablica[simbol.uppercase()]

    /** Cijene po jedinicama, u valuti kotacije svedenoj na glavnu jedinicu (npr. centi → dolari). */
    fun cijene(simbol: String, cijena: Double, valuta: String): List<Pair<String, Double>> {
        val p = za(simbol) ?: return emptyList()
        val (_, f) = Portfelj.normalizirajValutu(valuta)
        return p.jedinice.map { (n, k) -> n to cijena * f * k }
    }
}
