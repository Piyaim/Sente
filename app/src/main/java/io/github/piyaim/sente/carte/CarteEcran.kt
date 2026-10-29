package io.github.piyaim.sente.carte

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.piyaim.sente.perso.Raccourci
import io.github.piyaim.sente.position.SourcePosition
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.regions.RegionInstallee
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.PastilleCarte
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.max

private const val ZOOM_REGION = 7.5
private const val ZOOM_POSITION = 15.0
private const val ZOOM_LIEU = 16.0
private const val SOURCE_SELECTION = "sente-selection"

@Composable
fun CarteEcran(
    region: RegionInstallee?,
    sombre: Boolean,
    positionAutorisee: Boolean,
    utiliserReseau: Boolean,
    selection: Lieu?,
    onDemanderPosition: () -> Unit,
    onOuvrirRegions: () -> Unit,
    onRechercher: () -> Unit,
    onRaccourci: (Raccourci) -> Unit,
    onFavoris: () -> Unit,
    onPosition: (Location) -> Unit,
    onCentre: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surPosition by rememberUpdatedState(onPosition)
    val surCentre by rememberUpdatedState(onCentre)

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
            m.addOnCameraIdleListener { m.cameraPosition.target?.let { surCentre(it.latitude, it.longitude) } }
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
            surPosition = {
                position = it
                surPosition(it)
            },
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
        if (!dejaCentreSurPosition && selection == null) {
            suivrePosition(m)
            dejaCentreSurPosition = true
        }
    }

    // Lieu choisi : repère doré et caméra centrée au-dessus de la fiche.
    val margeFiche = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp * 0.55f).dp.toPx() }.toDouble()
    LaunchedEffect(style, selection) {
        val s = style ?: return@LaunchedEffect
        majRepere(s, selection)
    }
    LaunchedEffect(carte, selection) {
        val m = carte ?: return@LaunchedEffect
        if (selection != null) {
            if (composantActif) m.locationComponent.setCameraMode(CameraMode.NONE)
            dejaCentreSurPosition = true
            val zoom = max(m.cameraPosition.zoom, ZOOM_LIEU)
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(selection.lat, selection.lon))
                        .zoom(zoom)
                        .padding(0.0, 0.0, 0.0, margeFiche)
                        .build(),
                ),
                600,
            )
        } else {
            m.animateCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0), 300)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { vueCarte }, modifier = Modifier.fillMaxSize())

        if (region == null && selection == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(28.dp),
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

        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (selection == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BarreRecherche(onClick = onRechercher, modifier = Modifier.weight(1f))
                        BoutonIcone(Icones.Calques, "Cartes hors ligne et réglages", onOuvrirRegions)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PastilleCarte(Icones.Domicile, "Domicile") { onRaccourci(Raccourci.DOMICILE) }
                        PastilleCarte(Icones.Travail, "Travail") { onRaccourci(Raccourci.TRAVAIL) }
                        PastilleCarte(Icones.Etoile, "Favoris", onFavoris)
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
                                .align(Alignment.CenterHorizontally)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                BoutonIcone(
                    icone = Icones.Viseur,
                    description = "Centrer sur ma position",
                    onClick = {
                        val m = carte
                        when {
                            !positionAutorisee -> onDemanderPosition()
                            m != null && composantActif && position != null -> suivrePosition(m)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp),
                    taille = 64.dp,
                    rayon = 22.dp,
                    fond = MaterialTheme.colorScheme.primary,
                    encre = MaterialTheme.colorScheme.onPrimary,
                )
            }

            if (region != null) {
                Text(
                    "© OpenStreetMap",
                    modifier = Modifier
                        .align(if (selection == null) Alignment.BottomStart else Alignment.TopEnd)
                        .padding(bottom = if (selection == null) 24.dp else 0.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .clickable(role = Role.Button) { attributionsOuvertes = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        "Adresses : Base Adresse Nationale, Licence Ouverte 2.0.\n\n" +
                        "Tuiles et style : Protomaps (style CC0, adapté). Icônes de carte : tangrams/icons (MIT). " +
                        "Polices : Noto Sans, Atkinson Hyperlegible Next, Bricolage Grotesque (SIL Open Font License).\n\n" +
                        "Affichage : MapLibre Native (BSD).",
                )
            },
        )
    }
}

@Composable
private fun BarreRecherche(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = "Rechercher", onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icones.Recherche, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            "Rechercher une adresse, un lieu",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Repère doré du lieu choisi : un halo et un point cerclé d'encre, ajoutés au style courant. */
private fun majRepere(style: Style, lieu: Lieu?) {
    val geojson = if (lieu == null) {
        """{"type":"FeatureCollection","features":[]}"""
    } else {
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${
            "%.7f".format(Locale.ROOT, lieu.lon)
        },${"%.7f".format(Locale.ROOT, lieu.lat)}]}}]}"""
    }
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_SELECTION)
    if (source != null) {
        source.setGeoJson(geojson)
        return
    }
    style.addSource(GeoJsonSource(SOURCE_SELECTION, geojson))
    style.addLayer(
        CircleLayer("$SOURCE_SELECTION-halo", SOURCE_SELECTION).withProperties(
            PropertyFactory.circleRadius(22f),
            PropertyFactory.circleColor("#E9C46A"),
            PropertyFactory.circleOpacity(0.35f),
        ),
    )
    style.addLayer(
        CircleLayer("$SOURCE_SELECTION-point", SOURCE_SELECTION).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#E9C46A"),
            PropertyFactory.circleStrokeColor("#16211B"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
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
