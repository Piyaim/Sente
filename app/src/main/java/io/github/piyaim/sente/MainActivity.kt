package io.github.piyaim.sente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.piyaim.sente.carte.CarteEcran
import io.github.piyaim.sente.regions.GestionRegions
import io.github.piyaim.sente.regions.RegionsEcran
import io.github.piyaim.sente.ui.SenteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SenteTheme { AppSente() } }
    }
}

@Composable
private fun AppSente() {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val gestion = remember { GestionRegions(context.applicationContext, portee) }
    var ecranRegions by rememberSaveable { mutableStateOf(false) }
    var positionAutorisee by remember { mutableStateOf(positionPreciseAccordee(context)) }

    val demandePosition = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        positionAutorisee = positionPreciseAccordee(context)
    }
    val demander = {
        demandePosition.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        gestion.suivreTelechargement()
        if (!positionAutorisee) demander()
    }

    BackHandler(enabled = ecranRegions) { ecranRegions = false }

    Box(Modifier.fillMaxSize()) {
        // La carte reste en place sous l'écran des régions : pas de rechargement en revenant.
        CarteEcran(
            region = gestion.affichee,
            sombre = isSystemInDarkTheme(),
            positionAutorisee = positionAutorisee,
            utiliserReseau = gestion.fournisseurReseau,
            onDemanderPosition = demander,
            onOuvrirRegions = { ecranRegions = true },
        )
        if (ecranRegions) {
            Surface(Modifier.fillMaxSize()) {
                RegionsEcran(gestion = gestion, onRetour = { ecranRegions = false })
            }
        }
    }
}

/** Le GPS exige la position précise ; la position approximative seule ne suffit pas. */
private fun positionPreciseAccordee(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
