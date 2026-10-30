package io.github.piyaim.sente.itineraire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Bande provisoire de l'essai 3a : durée, distance, heure d'arrivée et temps de calcul.
 * Les vrais écrans de trajet viendront des maquettes de l'étape 3b.
 */
@Composable
fun BandeTrajet(trajet: Trajet, onFermer: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${dureeLisible(trajet.dureeSecondes)} · ${distanceKmLisible(trajet.distanceKm)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                )
                TextButton(onClick = onFermer) { Text("Fermer") }
            }
            Text(
                "Vers ${trajet.arrivee.nom} · arrivée vers ${heureArrivee(trajet.dureeSecondes)}",
                modifier = Modifier.padding(end = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Essai : calculé sur le téléphone en ${"%.1f".format(Locale.FRANCE, trajet.calculMs / 1000.0)} s · guidage à venir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** « 8 min », « 1 h 05 ». */
fun dureeLisible(secondes: Double): String {
    val minutes = (secondes / 60).roundToLong().coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/** « 850 m », « 14,2 km », « 112 km ». */
fun distanceKmLisible(km: Double): String = when {
    km < 0.95 -> "${(km * 100).roundToLong() * 10} m"
    km < 100 -> "%.1f km".format(Locale.FRANCE, km)
    else -> "${km.roundToLong()} km"
}

private fun heureArrivee(secondes: Double): String =
    LocalTime.now().plusSeconds(secondes.roundToLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
