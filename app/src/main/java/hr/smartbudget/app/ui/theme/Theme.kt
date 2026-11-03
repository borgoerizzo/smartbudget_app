package hr.smartbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Tamni neon (dizajn "Kućni budžet"). */
object Neon {
    val Pozadina = Color(0xFF0B0F14)
    val Kartica = Color(0xC71A222D)          // rgba(26,34,45,.78)
    val KarticaSvijetla = Color(0xE0222E3D)  // rgba(34,46,61,.88)
    val Nav = Color(0xE6121820)
    val Sheet = Color(0xF7151C26)
    val Rub = Color(0x17FFFFFF)              // rgba(255,255,255,.09)
    val RubSlab = Color(0x0FFFFFFF)
    val Ispuna = Color(0x0AFFFFFF)           // rgba(255,255,255,.04)
    val Staza = Color(0x14FFFFFF)            // rgba(255,255,255,.08)

    val Tekst = Color(0xFFF2F6FA)
    val TekstSiv = Color(0xFF9AA7B8)
    val TekstSvijetliSiv = Color(0xFFC3CEDB)
    val TekstSrednji = Color(0xFFB4C0CE)
    val Neaktivno = Color(0xFF5B6778)

    val Zelena = Color(0xFF00F5A0)
    val Cijan = Color(0xFF00D8F6)
    val Crvena = Color(0xFFFF5376)
    val CrvenaJaka = Color(0xFFFF2A6D)
    val Jantar = Color(0xFFFFC247)
    val Narancasta = Color(0xFFFF8A3D)
    val Ljubicasta = Color(0xFF9D4EDD)
    val LjubicastaSvijetla = Color(0xFFB06AF5)
    val LjubicastaTekst = Color(0xFFC79BF5)
    val NaGumbu = Color(0xFF04140E)

    val paleta = listOf(
        "#FFC247", "#FF8A3D", "#F15BB5", "#9D4EDD", "#4D8BFF", "#00D8F6", "#2DD4BF", "#BEF264", "#00F5A0", "#FF5376",
    )
}

fun hexBoja(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Neon.Cijan)

/** Svjetlija verzija boje kategorije za tekst/ikonu na tamnoj podlozi. */
fun Color.svjetlija(f: Float = 0.25f) = Color(
    red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha,
)

private val tnum = "tnum"

object Tip {
    val naslovVeliki = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = Neon.Tekst)
    val naslov = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Neon.Tekst)
    val naslovKartice = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Neon.Tekst)
    val tijelo = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Neon.Tekst, fontFeatureSettings = tnum)
    val malo = TextStyle(fontSize = 13.sp, color = Neon.TekstSiv, fontFeatureSettings = tnum)
    val oznaka = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Neon.TekstSiv, fontFeatureSettings = tnum)
    val iznos = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = tnum)
}

@Composable
fun SmartBudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Zelena,
            onPrimary = Neon.NaGumbu,
            secondary = Neon.Cijan,
            background = Neon.Pozadina,
            surface = Neon.Sheet,
            onSurface = Neon.Tekst,
            onBackground = Neon.Tekst,
            surfaceContainerHigh = Color(0xFF151C26),
            error = Neon.Crvena,
        ),
        content = content,
    )
}
