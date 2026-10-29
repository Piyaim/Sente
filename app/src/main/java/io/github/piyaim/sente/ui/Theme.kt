package io.github.piyaim.sente.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.piyaim.sente.R
import java.util.Locale

/** Vert forêt pour les actions, or chaud pour le lieu choisi ; fonds blancs et gris-vert pour laisser la carte devant. */
object Couleurs {
    val Foret = Color(0xFF1F4D3A)
    val Or = Color(0xFFE9C46A)
    val Encre = Color(0xFF16211B)
}

private val Clair = lightColorScheme(
    primary = Couleurs.Foret,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8DF),
    onPrimaryContainer = Color(0xFF12392A),
    secondary = Color(0xFF4D5C53),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBE5),
    onSecondaryContainer = Couleurs.Encre,
    tertiary = Couleurs.Or,
    onTertiary = Couleurs.Encre,
    background = Color(0xFFF3F5F1),
    onBackground = Couleurs.Encre,
    surface = Color.White,
    onSurface = Couleurs.Encre,
    surfaceVariant = Color(0xFFE6EBE5),
    onSurfaceVariant = Color(0xFF4D5C53),
    surfaceTint = Couleurs.Foret,
    outline = Color(0xFF8A968E),
    outlineVariant = Color(0xFFE3E8E2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9F6),
    surfaceContainer = Color(0xFFF3F5F1),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    inverseSurface = Color(0xFF2B332E),
    inverseOnSurface = Color(0xFFEFF2EE),
    inversePrimary = Color(0xFF9FD3B5),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF9FD3B5),
    onPrimary = Color(0xFF0B2A1E),
    primaryContainer = Color(0xFF2A3B31),
    onPrimaryContainer = Color(0xFFDCE8DF),
    secondary = Color(0xFFB5C1B8),
    onSecondary = Color(0xFF1F2A23),
    secondaryContainer = Color(0xFF2A322D),
    onSecondaryContainer = Color(0xFFE3E8E2),
    tertiary = Couleurs.Or,
    onTertiary = Couleurs.Encre,
    background = Color(0xFF101512),
    onBackground = Color(0xFFE3E8E2),
    surface = Color(0xFF161C18),
    onSurface = Color(0xFFE3E8E2),
    surfaceVariant = Color(0xFF2A322D),
    onSurfaceVariant = Color(0xFFB5C1B8),
    surfaceTint = Color(0xFF9FD3B5),
    outline = Color(0xFF6D7A71),
    outlineVariant = Color(0xFF2F3833),
    surfaceContainerLowest = Color(0xFF0C100E),
    surfaceContainerLow = Color(0xFF141A16),
    surfaceContainer = Color(0xFF181F1B),
    surfaceContainerHigh = Color(0xFF1E2621),
    surfaceContainerHighest = Color(0xFF242D27),
    inverseSurface = Color(0xFFE3E8E2),
    inverseOnSurface = Color(0xFF1F2A23),
    inversePrimary = Couleurs.Foret,
)

/** Atkinson Hyperlegible Next (lisibilité, y compris en conduite) et Bricolage Grotesque pour les titres. */
private val Corps = FontFamily(
    Font(R.font.atkinson_regulier, FontWeight.Normal),
    Font(R.font.atkinson_semigras, FontWeight.SemiBold),
    Font(R.font.atkinson_gras, FontWeight.Bold),
)
private val Titres = FontFamily(Font(R.font.bricolage_gras, FontWeight.Bold))

private fun Typography.avecPolices(): Typography {
    fun TextStyle.corps() = copy(fontFamily = Corps)
    fun TextStyle.titre() = copy(fontFamily = Titres, fontWeight = FontWeight.Bold)
    return copy(
        displayLarge = displayLarge.titre(), displayMedium = displayMedium.titre(), displaySmall = displaySmall.titre(),
        headlineLarge = headlineLarge.titre(), headlineMedium = headlineMedium.titre(),
        headlineSmall = headlineSmall.titre().copy(fontSize = 28.sp, lineHeight = 32.sp),
        titleLarge = titleLarge.corps().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.corps().copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
        titleSmall = titleSmall.corps().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.corps().copy(fontSize = 17.sp), bodyMedium = bodyMedium.corps().copy(fontSize = 15.sp),
        bodySmall = bodySmall.corps().copy(fontSize = 14.sp),
        labelLarge = labelLarge.corps().copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.corps().copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.corps().copy(fontWeight = FontWeight.Bold),
    )
}

private val Formes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SenteTheme(sombre: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (sombre) Sombre else Clair,
        typography = Typography().avecPolices(),
        shapes = Formes,
        content = content,
    )
}

/** « 1 234 567 890 » octets → « 1,1 Go », « 512 Mo ». */
fun tailleLisible(octets: Long): String {
    val mo = octets / 1_048_576.0
    return if (mo >= 1024) "%.1f Go".format(Locale.FRANCE, mo / 1024) else "%.0f Mo".format(Locale.FRANCE, mo)
}

/** « 20261028 » → « 28/10/2026 ». */
fun dateLisible(aaaammjj: String?): String? =
    aaaammjj?.takeIf { it.length == 8 }?.let { "${it.substring(6, 8)}/${it.substring(4, 6)}/${it.substring(0, 4)}" }
