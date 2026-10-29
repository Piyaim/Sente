package io.github.piyaim.sente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// Couleurs d'identité provisoires : vert forêt et sable, celles du chemin.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4D3A),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1B1C19),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9C46A),
    onPrimary = Color(0xFF2A2100),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE3E3DD),
    surface = Color(0xFF121614),
    onSurface = Color(0xFFE3E3DD),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SenteTheme { AccueilProvisoire() } }
    }
}

@Composable
private fun SenteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

@Composable
private fun AccueilProvisoire() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.preview_notice),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
