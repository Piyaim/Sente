package io.github.piyaim.sente.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Couleurs d'identité provisoires : vert forêt et sable. Le vrai design viendra avec les maquettes.
private val Clair = lightColorScheme(
    primary = Color(0xFF1F4D3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE6D6),
    onPrimaryContainer = Color(0xFF07211A),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1B1C19),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFFE9C46A),
    onPrimary = Color(0xFF2A2100),
    primaryContainer = Color(0xFF3D4F45),
    onPrimaryContainer = Color(0xFFE3E3DD),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE3E3DD),
    surface = Color(0xFF121614),
    onSurface = Color(0xFFE3E3DD),
)

@Composable
fun SenteTheme(sombre: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (sombre) Sombre else Clair, content = content)
}

/** « 1 234 567 890 » octets → « 1,1 Go », « 512 Mo ». */
fun tailleLisible(octets: Long): String {
    val mo = octets / 1_048_576.0
    return if (mo >= 1024) "%.1f Go".format(java.util.Locale.FRANCE, mo / 1024) else "%.0f Mo".format(java.util.Locale.FRANCE, mo)
}

/** « 20261028 » → « 28/10/2026 ». */
fun dateLisible(aaaammjj: String?): String? =
    aaaammjj?.takeIf { it.length == 8 }?.let { "${it.substring(6, 8)}/${it.substring(4, 6)}/${it.substring(0, 4)}" }
