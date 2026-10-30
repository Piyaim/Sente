package io.github.piyaim.sente.carte

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.piyaim.sente.perso.Raccourci
import io.github.piyaim.sente.position.SourcePosition
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.recherche.VueCategorie
import io.github.piyaim.sente.regions.RegionInstallee
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.PastilleCarte
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

private const val ZOOM_REGION = 7.5
private const val ZOOM_POSITION = 15.0
private const val ZOOM_LIEU = 16.0
private const val ZOOM_GUIDAGE = 17.0
private const val SOURCE_SELECTION = "sente-selection"
private const val SOURCE_CATEGORIE = "sente-categorie"
private const val SOURCE_TRAJET = "sente-trajet"
private const val SOURCE_VARIANTE = "sente-variante"
private val MARGE_BAS_CATEGORIE = 300.dp // bande du bas et bouton de position
private val MARGE_HAUT_CATEGORIE = 190.dp // barre de catégorie et bouton « Liste »

/** Trajet dessiné sur la carte : en aperçu (tout le tracé visible) ou en guidage (la carte suit la voiture). */
data class AffichageTrajet(
    val trace: List<Reference>,
    val variante: List<Reference>?,
    val arrivee: Lieu,
    val guidage: Boolean,
)

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
    categorie: VueCategorie? = null,
    onPointCategorie: (Int) -> Unit = {},
    onListeCategorie: () -> Unit = {},
    onFermerCategorie: () -> Unit = {},
    trajet: AffichageTrajet? = null,
    positionGuidage: Location? = null,
    recentrage: Int = 0,
    onSuivi: (Boolean) -> Unit = {},
    bandeauBas: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surPosition by rememberUpdatedState(onPosition)
    val surCentre by rememberUpdatedState(onCentre)
    val surPointCategorie by rememberUpdatedState(onPointCategorie)
    val surSuivi by rememberUpdatedState(onSuivi)
    val toleranceTouche = with(LocalDensity.current) { 24.dp.toPx() }

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
            // Toucher un point d'une catégorie le met en avant (un peu de marge : les points sont petits).
            m.addOnMapClickListener { point ->
                val p = m.projection.toScreenLocation(point)
                val zone = RectF(p.x - toleranceTouche, p.y - toleranceTouche, p.x + toleranceTouche, p.y + toleranceTouche)
                val i = m.queryRenderedFeatures(zone, "$SOURCE_CATEGORIE-point").firstOrNull()?.getNumberProperty("i")?.toInt()
                if (i != null) surPointCategorie(i)
                i != null
            }
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
        // Pendant le guidage, le point suit la position recalée sur la route (ci-dessous), pas le GPS brut.
        if (trajet?.guidage == true) return@LaunchedEffect
        m.locationComponent.forceLocationUpdate(p)
        if (!dejaCentreSurPosition && selection == null) {
            suivrePosition(m)
            dejaCentreSurPosition = true
        }
    }

    // Lieu choisi (ou mis en avant dans une catégorie) : repère doré ; caméra au-dessus de la fiche ou de la bande.
    val densite = LocalDensity.current
    val margeFiche = with(densite) { (LocalConfiguration.current.screenHeightDp * 0.55f).dp.toPx() }.toDouble()
    val margeBande = with(densite) { MARGE_BAS_CATEGORIE.toPx() }
    val margeHaut = with(densite) { MARGE_HAUT_CATEGORIE.toPx() }
    val margeCote = with(densite) { 48.dp.toPx() }
    val margeHautApercu = with(densite) { 150.dp.toPx() }
    val couleurPoints = MaterialTheme.colorScheme.primary.toArgb()
    val couleurTrajet = MaterialTheme.colorScheme.primary.toArgb()
    val couleurVariante = MaterialTheme.colorScheme.outline.toArgb()
    val trace = trajet?.trace
    val variante = trajet?.variante
    val guidage = trajet?.guidage == true
    LaunchedEffect(style, selection, categorie, trace, variante, trajet?.arrivee) {
        val s = style ?: return@LaunchedEffect
        // Ordre d'empilement : variante grise, tracé, puis points de catégorie, puis repère doré au-dessus.
        majLigne(s, SOURCE_VARIANTE, variante, couleurVariante)
        majLigne(s, SOURCE_TRAJET, trace, couleurTrajet)
        majPointsCategorie(s, if (trajet == null) categorie?.lieux.orEmpty() else emptyList(), couleurPoints)
        majRepere(s, selection ?: trajet?.arrivee ?: categorie?.choisi)
    }
    var listeCadree by remember { mutableStateOf<List<Lieu>?>(null) }
    LaunchedEffect(carte, selection, categorie, trace, variante, guidage) {
        val m = carte ?: return@LaunchedEffect
        val vue = categorie
        if (selection != null || vue != null || trace != null) {
            if (composantActif && !guidage) m.locationComponent.setCameraMode(CameraMode.NONE)
            dejaCentreSurPosition = true
        }
        when {
            selection != null -> versLieu(m, selection, margeFiche)
            guidage -> Unit // caméra gérée par le suivi du guidage, ci-dessous
            trace != null -> {
                // Aperçu : tout le tracé (et la variante) entre la carte du haut et le panneau du bas.
                val bornes = runCatching {
                    LatLngBounds.Builder().includes((trace + variante.orEmpty()).map { LatLng(it.lat, it.lon) }).build()
                }.getOrNull()
                if (bornes != null) {
                    m.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bornes, margeCote.toInt(), margeHautApercu.toInt(), margeCote.toInt(), margeFiche.toInt(),
                        ),
                        600,
                    )
                }
            }
            vue != null -> {
                val nouvelle = listeCadree != vue.lieux
                listeCadree = vue.lieux
                // Nouvelle liste : on montre tous les points ; ensuite on suit le lieu mis en avant.
                val bornes = if (nouvelle && vue.lieux.size > 1) {
                    runCatching { LatLngBounds.Builder().includes(vue.lieux.map { LatLng(it.lat, it.lon) }).build() }.getOrNull()
                } else {
                    null
                }
                if (bornes != null) {
                    m.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bornes, margeCote.toInt(), margeHaut.toInt(), margeCote.toInt(), margeBande.toInt(),
                        ),
                        600,
                    )
                } else {
                    versLieu(m, vue.choisi, margeBande.toDouble())
                }
            }
            else -> {
                listeCadree = null
                m.animateCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0), 300)
            }
        }
    }

    // Guidage : la carte suit la voiture, dans le sens de la marche, point placé dans le bas de l'écran.
    val margeGuidage = with(densite) { (LocalConfiguration.current.screenHeightDp * 0.42f).dp.toPx() }.toDouble()
    var etaitEnGuidage by remember { mutableStateOf(false) }
    // Relancé aussi quand la taille change (entrée ou sortie de la petite fenêtre flottante).
    LaunchedEffect(carte, composantActif, guidage, recentrage, margeGuidage) {
        val m = carte ?: return@LaunchedEffect
        if (!composantActif) return@LaunchedEffect
        val composant = m.locationComponent
        if (guidage) {
            etaitEnGuidage = true
            m.moveCamera(CameraUpdateFactory.paddingTo(0.0, margeGuidage, 0.0, 0.0))
            composant.renderMode = RenderMode.GPS
            composant.setCameraMode(CameraMode.TRACKING_GPS, 750L, ZOOM_GUIDAGE, null, 45.0, null)
            surSuivi(true)
        } else if (etaitEnGuidage) {
            etaitEnGuidage = false
            composant.setCameraMode(CameraMode.NONE)
            composant.renderMode = RenderMode.COMPASS
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).tilt(0.0).bearing(0.0).padding(0.0, 0.0, 0.0, 0.0).build()), 500)
        }
    }
    LaunchedEffect(positionGuidage, composantActif) {
        val m = carte ?: return@LaunchedEffect
        val p = positionGuidage ?: return@LaunchedEffect
        if (composantActif) m.locationComponent.forceLocationUpdate(p)
    }
    // Un geste sur la carte interrompt le suivi : le guidage affiche alors « Recentrer ».
    DisposableEffect(carte, composantActif) {
        val m = carte
        if (m == null || !composantActif) {
            onDispose { }
        } else {
            val ecoute = object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() = surSuivi(false)
                override fun onCameraTrackingChanged(currentMode: Int) = Unit
            }
            m.locationComponent.addOnCameraTrackingChangedListener(ecoute)
            onDispose { m.locationComponent.removeOnCameraTrackingChangedListener(ecoute) }
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
            val centrer: () -> Unit = {
                val m = carte
                when {
                    !positionAutorisee -> onDemanderPosition()
                    m != null && composantActif && position != null -> suivrePosition(m)
                }
            }
            val attribution: @Composable (Modifier) -> Unit = { modifier ->
                Text(
                    "© OpenStreetMap",
                    modifier = modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .clickable(role = Role.Button) { attributionsOuvertes = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            val modeTrajet = selection == null && trajet != null
            val modeCategorie = selection == null && trajet == null && categorie != null

            if (modeTrajet) {
                // Aperçu et guidage dessinent leurs propres commandes ; seule la mention des sources reste ici.
                if (region != null) {
                    attribution(
                        if (guidage) {
                            Modifier.align(Alignment.BottomStart).padding(bottom = 214.dp)
                        } else {
                            Modifier.align(Alignment.TopEnd).padding(top = 92.dp)
                        },
                    )
                }
            } else if (modeCategorie && categorie != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    BarreCategorie(categorie, onRetour = onListeCategorie, onFermer = onFermerCategorie)
                    PastilleCarte(Icones.Liste, "Liste", onListeCategorie)
                }
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        if (region != null) attribution(Modifier)
                        Spacer(Modifier.weight(1f))
                        BoutonIcone(Icones.Viseur, "Centrer sur ma position", centrer, rayon = 20.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    bandeauBas?.invoke()
                }
            } else if (selection == null) {
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
                    onClick = centrer,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp),
                    taille = 64.dp,
                    rayon = 22.dp,
                    fond = MaterialTheme.colorScheme.primary,
                    encre = MaterialTheme.colorScheme.onPrimary,
                )
            }

            if (region != null && !modeCategorie && !modeTrajet) {
                attribution(
                    Modifier
                        .align(if (selection == null) Alignment.BottomStart else Alignment.TopEnd)
                        .padding(bottom = if (selection == null) 24.dp else 0.dp),
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

/** Barre du haut en mode catégorie : retour à la liste, nom de la catégorie, nombre de résultats, fermer. */
@Composable
private fun BarreCategorie(vue: VueCategorie, onRetour: () -> Unit, onFermer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .height(56.dp)
            .padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoutonIcone(
            Icones.Retour, "Retour à la liste", onRetour,
            taille = 48.dp, rayon = 24.dp, fond = MaterialTheme.colorScheme.surface,
            encre = MaterialTheme.colorScheme.onSurface, ombre = false,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            vue.libelle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        Text(
            if (vue.lieux.size > 1) "${vue.lieux.size} résultats" else "1 résultat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        BoutonIcone(
            Icones.Effacer, "Fermer et revenir à la carte", onFermer,
            taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.secondaryContainer,
            encre = MaterialTheme.colorScheme.onSurface, ombre = false,
        )
    }
}

/** Caméra centrée sur un lieu, au-dessus d'une fiche ou d'une bande qui occupe [margeBas] pixels en bas. */
private fun versLieu(carte: MapLibreMap, lieu: Lieu, margeBas: Double) {
    carte.animateCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(lieu.lat, lieu.lon))
                .zoom(max(carte.cameraPosition.zoom, ZOOM_LIEU))
                .padding(0.0, 0.0, 0.0, margeBas)
                .build(),
        ),
        600,
    )
}

/** Une ligne de trajet (tracé ou variante) bordée de blanc, sous les points et le repère. */
private fun majLigne(style: Style, id: String, points: List<Reference>?, couleur: Int) {
    val coordonnees = JSONArray()
    points?.forEach { coordonnees.put(JSONArray().put(it.lon).put(it.lat)) }
    val geojson = if (coordonnees.length() < 2) {
        """{"type":"FeatureCollection","features":[]}"""
    } else {
        JSONObject()
            .put("type", "Feature")
            .put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordonnees))
            .toString()
    }
    val source = style.getSourceAs<GeoJsonSource>(id)
    if (source != null) {
        source.setGeoJson(geojson)
        return
    }
    style.addSource(GeoJsonSource(id, geojson))
    val bord = LineLayer("$id-bord", id).withProperties(
        PropertyFactory.lineColor("#FFFFFF"),
        PropertyFactory.lineWidth(11f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val trait = LineLayer("$id-trait", id).withProperties(
        PropertyFactory.lineColor(couleur),
        PropertyFactory.lineWidth(7f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val dessous = listOf("$SOURCE_CATEGORIE-point", "$SOURCE_SELECTION-halo").firstOrNull { style.getLayer(it) != null }
    if (dessous != null) {
        style.addLayerBelow(bord, dessous)
        style.addLayerBelow(trait, dessous)
    } else {
        style.addLayer(bord)
        style.addLayer(trait)
    }
}

/** Points verts des résultats d'une catégorie, sous le repère doré. Le numéro « i » sert au toucher. */
private fun majPointsCategorie(style: Style, lieux: List<Lieu>, couleur: Int) {
    val points = JSONArray()
    lieux.forEachIndexed { i, lieu ->
        points.put(
            JSONObject()
                .put("type", "Feature")
                .put("properties", JSONObject().put("i", i))
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lieu.lon).put(lieu.lat))),
        )
    }
    val geojson = JSONObject().put("type", "FeatureCollection").put("features", points).toString()
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_CATEGORIE)
    if (source != null) {
        source.setGeoJson(geojson)
        return
    }
    style.addSource(GeoJsonSource(SOURCE_CATEGORIE, geojson))
    val point = CircleLayer("$SOURCE_CATEGORIE-point", SOURCE_CATEGORIE).withProperties(
        PropertyFactory.circleRadius(11f),
        PropertyFactory.circleColor(couleur),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(3f),
    )
    val centre = CircleLayer("$SOURCE_CATEGORIE-centre", SOURCE_CATEGORIE).withProperties(
        PropertyFactory.circleRadius(3.5f),
        PropertyFactory.circleColor("#FFFFFF"),
    )
    // Toujours sous le repère doré, qu'il existe déjà ou non.
    if (style.getLayer("$SOURCE_SELECTION-halo") != null) {
        style.addLayerBelow(point, "$SOURCE_SELECTION-halo")
        style.addLayerBelow(centre, "$SOURCE_SELECTION-halo")
    } else {
        style.addLayer(point)
        style.addLayer(centre)
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
