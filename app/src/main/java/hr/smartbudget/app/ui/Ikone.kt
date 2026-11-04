package hr.smartbudget.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Linijske ikone iz dizajna (SVG putanje 24×24, potez 2). */
object Ikone {
    private val putanje = mapOf(
        "cart" to "M3 4h2l2.4 10h9.6l1.8-7H6 M9 19.5h.01 M17 19.5h.01",
        "home" to "M4 11 12 4l8 7 M6 10v10h12V10 M10 20v-6h4v6",
        "car" to "M4 16v-4.5L6.5 6h11L20 11.5V16 M4 16h16 M4 16v2.5h2.5V16 M17.5 16v2.5H20V16 M4 11.5h16",
        "bolt" to "M13 2 4 14h7l-1 8 9-12h-7z",
        "coffee" to "M4 8h12v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z M16 10h2a2 2 0 0 1 0 4h-2 M7 3v2 M10 3v2 M13 3v2",
        "heart" to "M12 20s-7-4.6-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.4-7 10-7 10z",
        "star" to "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
        "repeat" to "M17 2l3 3-3 3 M20 5H9a5 5 0 0 0-5 5 M7 22l-3-3 3-3 M4 19h11a5 5 0 0 0 5-5",
        "gift" to "M4 10h16v10H4z M3 7h18v3H3z M12 7v13 M12 7C10 3 6 4 7.5 7 M12 7c2-4 6-3 4.5 0",
        "plane" to "M21 3 10 14 M21 3l-7 18-4-7-7-4z",
        "book" to "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z M5 17a3 3 0 0 1 3-3h11",
        "shirt" to "M8 4 3 7l2 4 2-1v10h10V10l2 1 2-4-5-3a4 4 0 0 1-8 0z",
        "brief" to "M4 8h16v11H4z M9 8V6h6v2 M4 13h16",
        "card" to "M3 6h18v12H3z M3 10h18 M7 15h4",
        "bank" to "M3 10l9-6 9 6 M5 10v8 M9 10v8 M15 10v8 M19 10v8 M3 20h18",
        "target" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18 M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M12 12h.01",
        "wallet" to "M4 7h15v12H4z M4 7l11-3v3 M15 13h2",
        "phone" to "M8 3h8v18H8z M11 18h2",
        "paw" to "M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M16 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M12 20c-3 0-5-2-5-4s2-4 5-4 5 2 5 4-2 4-5 4",
        "child" to "M12 7a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5 M7 10h10 M12 10v6 M9 22l3-6 3 6",
        "piggy" to "M5 11a7 6 0 0 1 13-2h2v4h-2a7 6 0 0 1-3 4v3h-3v-2h-2v2H7v-3a6 6 0 0 1-2-6z M14 9h.01",
    )
    val nazivi: List<String> = putanje.keys.toList()

    private val cache = HashMap<String, ImageVector>()

    fun ikona(naziv: String): ImageVector = cache.getOrPut(naziv) { izPutanje(naziv, putanje[naziv] ?: putanje.getValue("star")) }

    private val ui = mapOf(
        "plus" to "M12 5v14 M5 12h14",
        "minus" to "M5 12h14",
        "close" to "M6 6l12 12 M18 6 6 18",
        "check" to "M5 12l5 5 9-12",
        "calendar" to "M4 6h16v14H4z M4 10h16 M8 3v4 M16 3v4",
        "chevDown" to "M6 9l6 6 6-6",
        "chevUp" to "M6 15l6-6 6 6",
        "chevLeft" to "M15 6l-6 6 6 6",
        "chevRight" to "M9 6l6 6-6 6",
        "edit" to "M4 20l1-4 11-11 3 3L8 19z M14 7l3 3",
        "filter" to "M4 5h16l-6 8v6l-4-2v-4z",
        "arrowDownLeft" to "M17 7 7 17 M16 17H7V8",
        "arrowUpRight" to "M7 17 17 7 M8 7h9v9",
        "warn" to "M12 4 3 20h18z M12 10v4 M12 17h.01",
        "navHome" to "M4 11 12 4l8 7 M6 10v10h12V10 M10 20v-6h4v6",
        "navChart" to "M4 20h16 M7 16v-5 M12 16V6 M17 16v-8",
        "navGrid" to "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z",
        "navSliders" to "M4 7h10 M18 7h2 M4 17h2 M10 17h10 M16 5v4 M8 15v4",
        "download" to "M12 4v11 M7 10l5 5 5-5 M5 20h14",
        "share" to "M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M8.6 13.5l6.8 4 M15.4 6.5l-6.8 4",
        "euro" to "M17 6a7 7 0 1 0 0 12 M4 10h9 M4 14h9",
        "trash" to "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13",
        "clock" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18 M12 7v5l3 2",
        "x" to "M6 6l12 12 M18 6 6 18",
        "transfer" to "M4 8h15l-4-4 M20 16H5l4 4",
        "camera" to "M4 7h4l2-3h4l2 3h4v13H4z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8",
        "image" to "M4 4h16v16H4z M4 16l5-5 4 4 3-3 4 4 M15 9h.01",
        "split" to "M12 3v6 M12 9l-6 6v6 M12 9l6 6v6",
        "upload" to "M12 20V9 M7 14l5-5 5 5 M5 4h14",
        "lock" to "M6 11h12v10H6z M8 11V7a4 4 0 0 1 8 0v4 M12 15v2",
        "fingerprint" to "M12 11v3a8 8 0 0 1-1.5 4.5 M8.5 8.5A5 5 0 0 1 17 12v1.5a12 12 0 0 1-.8 4.3 M5 15a12 12 0 0 0 .6-3A6.5 6.5 0 0 1 12 5.5 M15.4 6.2A6.5 6.5 0 0 1 18.5 12 M9 12a3 3 0 0 1 6 0v1",
        "backspace" to "M21 5H8l-6 7 6 7h13z M11 9l6 6 M17 9l-6 6",
        "eyeOff" to "M3 3l18 18 M10.6 6.1A10 10 0 0 1 12 6c6 0 10 6 10 6a17 17 0 0 1-3.2 3.8 M6.6 6.6C3.8 8.3 2 12 2 12s4 6 10 6c1.6 0 3-.4 4.3-1 M9.9 9.9a3 3 0 0 0 4.2 4.2",
        "bell" to "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z M10 21h4",
        "info" to "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18 M12 11v6 M12 7.5h.01",
        "search" to "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14 M16.5 16.5 21 21",
        "restore" to "M4 12a8 8 0 1 0 2.3-5.7 M4 4v4h4 M12 8v4l3 2",
    )

    /** Ikona sučelja; ako je nema, traži se među ikonama kategorija (nikad ne ruši aplikaciju). */
    fun ui(naziv: String): ImageVector = cache.getOrPut("ui:$naziv") {
        izPutanje(naziv, ui[naziv] ?: putanje[naziv] ?: putanje.getValue("star"))
    }

    private fun izPutanje(ime: String, d: String): ImageVector =
        ImageVector.Builder(ime, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
