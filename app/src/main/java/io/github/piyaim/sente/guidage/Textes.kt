package io.github.piyaim.sente.guidage

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Distance jusqu'à la prochaine manœuvre, arrondie pour être lue d'un coup d'œil : « 80 m », « 300 m », « 1,2 km ». */
fun distanceManoeuvre(metres: Double): String = when {
    metres < 100 -> "${((metres / 10).roundToLong() * 10).coerceAtLeast(10)} m"
    metres < 950 -> "${(metres / 50).roundToLong() * 50} m"
    metres < 10_000 -> "%.1f km".format(Locale.FRANCE, metres / 1000)
    else -> "${(metres / 1000).roundToLong()} km"
}

/** Distance restante : « 850 m », « 14,2 km », « 112 km ». */
fun distanceRestante(metres: Double): String = when {
    metres < 950 -> "${(metres / 50).roundToLong() * 50} m"
    metres < 100_000 -> "%.1f km".format(Locale.FRANCE, metres / 1000)
    else -> "${(metres / 1000).roundToLong()} km"
}

/** « 8 min », « 1 h 05 ». */
fun dureeLisible(secondes: Double): String {
    val minutes = (secondes / 60).roundToLong().coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/** Heure d'arrivée estimée : « 16:42 ». */
fun heureArrivee(secondesRestantes: Double): String =
    LocalTime.now().plusSeconds(secondesRestantes.roundToLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
