package io.github.piyaim.sente.carte

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.piyaim.sente.position.SourcePosition
import io.github.piyaim.sente.regions.RegionInstallee
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val ZOOM_REGION = 7.5
private const val ZOOM_POSITION = 15.0

@Composable
fun CarteEcran(
    region: RegionInstallee?,
    sombre: Boolean,
    positionAutorisee: Boolean,
    utiliserReseau: Boolean,
    onDemanderPosition: () -> Unit,
    onOuvrirRegions: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val vueCarte = remember {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    DisposableEffect(lifecycle) {
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_START -> vueCarte.onStart()
                Lifecycle.Event.ON_RESUME -> vueCarte.onResume()
                Lifecycle.Event.ON_PAUSE -> vueCarte.onPause()
                Lifecycle.Event.ON_STOP -> vueCarte.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observateur)
        onDispose { lifecycle.removeObserver(observateur) }
    }
    DisposableEffect(Unit) { onDispose { vueCarte.onDestroy() } }

    var carte by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var composantActif by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<Location?>(null) }
    var gpsActif by remember { mutableStateOf(true) }
    var dejaCentreSurPosition by rememberSaveable { mutableStateOf(false) }
    var regionCadree by rememberSaveable { mutableStateOf<String?>(null) }
    var attributionsOuvertes by remember { mutableStateOf(false) }

    LaunchedEffect(vueCarte) {
        vueCarte.getMapAsync { m ->
            m.uiSettings.setLogoEnabled(false)
            m.uiSettings.setAttributionEnabled(false) // remplacée par notre mention permanente ci-dessous
            carte = m
        }
    }

    // Le style change avec le thème ou la région (la date du fichier force le rechargement après une mise à jour).
    val cleStyle = "${region?.fichier?.absolutePath}|${region?.fichier?.lastModified()}|$sombre"
    LaunchedEffect(carte, cleStyle) {
        val m = carte ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromJson(StyleCarte.json(context, sombre, region?.fichier))) { s -> style = s }
    }

    // Cadre la région à son installation, tant que la position n'a pas pris la main.
    LaunchedEffect(carte, region?.id) {
        val m = carte ?: return@LaunchedEffect
        val r = region ?: return@LaunchedEffect
        if (regionCadree != r.id && !dejaCentreSurPosition) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(r.enTete.latCentre, r.enTete.lonCentre), ZOOM_REGION))
            regionCadree = r.id
        }
    }

    LaunchedEffect(style, positionAutorisee) {
        val m = carte ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (positionAutorisee && !composantActif) {
            activerPoint(context, m, s)
            composantActif = true
        }
    }

    // Position : seulement quand l'appli est visible (économie de batterie, pas de suivi en arrière-plan).
    DisposableEffect(lifecycle, positionAutorisee, utiliserReseau) {
        val source = SourcePosition(
            context = context,
            utiliserReseau = utiliserReseau,
            surPosition = { position = it },
            surEtatGps = { gpsActif = it },
        )
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_START -> if (positionAutorisee) source.demarrer()
                Lifecycle.Event.ON_STOP -> source.arreter()
                else -> Unit
            }
        }
        lifecycle.addObserver(observateur)
        onDispose {
            lifecycle.removeObserver(observateur)
            source.arreter()
        }
    }

    LaunchedEffect(position, composantActif) {
        val m = carte ?: return@LaunchedEffect
        val p = position ?: return@LaunchedEffect
        if (!composantActif) return@LaunchedEffect
        m.locationComponent.forceLocationUpdate(p)
        if (!dejaCentreSurPosition) {
            suivrePosition(m)
            dejaCentreSurPosition = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { vueCarte }, modifier = Modifier.fillMaxSize())

        if (region == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Aucune carte installée", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Téléchargez une région, ou importez un fichier de carte déjà copié sur le téléphone.",
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onOuvrirRegions) { Text("Cartes hors ligne") }
                }
            }
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            FilledTonalIconButton(onClick = onOuvrirRegions, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Settings, contentDescription = "Cartes hors ligne et réglages")
            }

            val bandeau = when {
                !positionAutorisee -> null
                !gpsActif -> "Localisation désactivée dans Android"
                position == null -> "Recherche du signal GPS…"
                else -> null
            }
            if (bandeau != null) {
                Text(
                    bandeau,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            FloatingActionButton(
                onClick = {
                    val m = carte
                    when {
                        !positionAutorisee -> onDemanderPosition()
                        m != null && composantActif && position != null -> suivrePosition(m)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Centrer sur ma position")
            }

            if (region != null) {
                Text(
                    "© OpenStreetMap",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .clickable { attributionsOuvertes = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    if (attributionsOuvertes) {
        AlertDialog(
            onDismissRequest = { attributionsOuvertes = false },
            confirmButton = { TextButton(onClick = { attributionsOuvertes = false }) { Text("Fermer") } },
            title = { Text("Sources de la carte") },
            text = {
                Text(
                    "Données : © les contributeurs d'OpenStreetMap, licence ODbL (openstreetmap.org/copyright).\n\n" +
                        "Tuiles et style : Protomaps (style CC0, adapté). Icônes : tangrams/icons (MIT). " +
                        "Polices : Noto Sans (SIL Open Font License).\n\n" +
                        "Affichage : MapLibre Native (BSD).",
                )
            },
        )
    }
}

@SuppressLint("MissingPermission") // appelée seulement quand la permission de position est accordée
private fun activerPoint(context: Context, carte: MapLibreMap, style: Style) {
    val composant = carte.locationComponent
    composant.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .useDefaultLocationEngine(false) // positions fournies par notre SourcePosition (LocationManager)
            .build(),
    )
    composant.setLocationComponentEnabled(true)
    composant.setRenderMode(RenderMode.COMPASS)
}

/** Suit la position, en zoomant pendant la transition (sans annuler le suivi). */
private fun suivrePosition(carte: MapLibreMap) {
    carte.locationComponent.setCameraMode(CameraMode.TRACKING, 750L, ZOOM_POSITION, null, null, null)
}
