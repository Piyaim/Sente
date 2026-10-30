package io.github.piyaim.sente.guidage

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.IResponse
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import io.github.piyaim.sente.itineraire.MoteurItineraire
import io.github.piyaim.sente.itineraire.OptionsTrajet
import io.github.piyaim.sente.itineraire.Proposition
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.Reference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.RouteRequest
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.io.File
import java.io.IOException
import java.util.Locale

/** Ce que l'écran de guidage affiche (maquettes « 7 · Guidage » et « 8 · Arrivée »). */
sealed interface EtatGuidage {
    data class EnCours(
        val destination: Lieu,
        val instruction: VisualInstruction?,
        val suivante: VisualInstruction?,
        val distanceManoeuvre: Double,
        val distanceRestante: Double,
        val dureeRestante: Double,
        val vitesseKmh: Int?,
        val position: Location,
        val trace: List<Reference>,
        val recalcul: Boolean,
        val simulation: Boolean,
    ) : EtatGuidage

    data class Arrive(val destination: Lieu, val dureeSecondes: Double, val distanceMetres: Double) : EtatGuidage
}

/**
 * Guidage avec le cœur de Ferrostar : suivi de la progression, consignes au bon moment, détection
 * d'écart et recalcul (fait par Valhalla, sur le téléphone). Aucune requête réseau : la seule source
 * de trajets est [MoteurItineraire].
 */
class GestionGuidage(context: Context, private val moteur: MoteurItineraire) {
    private val contexte = context.applicationContext
    private val service = GestionService(contexte)
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val voix = AndroidTtsObserver(
        contexte,
        statusObserver = object : AndroidTtsStatusListener {
            override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
                tts?.setLanguage(Locale.FRANCE)
            }

            override fun onTtsShutdownAndRelease() = Unit

            override fun onTtsSpeakError(utteranceId: String, status: Int) = Unit
        },
    )
    private var voixDemarree = false

    private var core: FerrostarCore? = null
    private var collecte: Job? = null
    private var destination: Lieu? = null
    private var simulation = false
    private var debut = 0L
    private var arrive = false
    private var geometrie: List<GeographicCoordinate>? = null
    private var trace: List<Reference> = emptyList()

    private val _etat = MutableStateFlow<EtatGuidage?>(null)
    val etat: StateFlow<EtatGuidage?> = _etat.asStateFlow()

    val muet: StateFlow<Boolean> get() = voix.muteState

    fun couperVoix(muet: Boolean) = voix.setMuted(muet)

    /**
     * Démarre le guidage sur [proposition]. Avec [simuler], une position fictive avance le long du
     * trajet : pour essayer le guidage sans rouler.
     */
    fun demarrer(proposition: Proposition, arrivee: Lieu, tuiles: File, options: OptionsTrajet, simuler: Boolean) {
        arreter()
        if (!voixDemarree) {
            voix.start()
            voixDemarree = true
        }
        destination = arrivee
        simulation = simuler
        arrive = false
        debut = System.currentTimeMillis()

        val positions: NavigationLocationProviding = if (simuler) {
            SimulatedLocationProvider(warpFactor = 2u).also { it.setRoute(proposition.route) }
        } else {
            PositionGuidage(contexte)
        }
        val recalcul = object : CustomRouteProvider {
            override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
                val cible = waypoints.lastOrNull()?.coordinate ?: GeographicCoordinate(arrivee.lat, arrivee.lon)
                val depart = Reference(userLocation.coordinates.lat, userLocation.coordinates.lng)
                return listOf(moteur.route(tuiles, depart, Reference(cible.lat, cible.lng), options))
            }
        }
        val nouveau = FerrostarCore(
            customRouteProvider = recalcul,
            httpClient = SansReseau,
            locationProvider = positions,
            navigationControllerConfig = configuration(),
            foregroundServiceManager = service,
        )
        nouveau.spokenInstructionObserver = voix
        core = nouveau
        nouveau.startNavigation(proposition.route)
        collecte = portee.launch { nouveau.state.collect { majEtat(it) } }
    }

    /** Arrête le guidage (bouton ✕ ou « Terminer » à l'arrivée). */
    fun arreter() {
        collecte?.cancel()
        collecte = null
        core?.stopNavigation()
        core = null
        geometrie = null
        trace = emptyList()
        _etat.value = null
    }

    private fun majEtat(etat: NavigationState) {
        val arrivee = destination ?: return
        when (val trajet = etat.tripState) {
            is TripState.Navigating -> {
                // La conversion du tracé n'est refaite que quand la route change (recalcul).
                if (etat.routeGeometry !== geometrie) {
                    geometrie = etat.routeGeometry
                    trace = etat.routeGeometry.map { Reference(it.lat, it.lng) }
                }
                _etat.value = EtatGuidage.EnCours(
                    destination = arrivee,
                    instruction = trajet.visualInstruction,
                    suivante = trajet.remainingSteps.getOrNull(1)?.visualInstructions?.firstOrNull(),
                    distanceManoeuvre = trajet.progress.distanceToNextManeuver,
                    distanceRestante = trajet.progress.distanceRemaining,
                    dureeRestante = trajet.progress.durationRemaining,
                    vitesseKmh = trajet.userLocation.speed?.value?.let { (it * 3.6).toInt() },
                    position = trajet.snappedUserLocation.toAndroidLocation(),
                    trace = trace,
                    recalcul = etat.isCalculatingNewRoute || trajet.deviation is RouteDeviation.Deviation,
                    simulation = simulation,
                )
            }
            is TripState.Complete -> {
                if (arrive) return
                arrive = true
                val resume = EtatGuidage.Arrive(
                    destination = arrivee,
                    dureeSecondes = (System.currentTimeMillis() - debut) / 1000.0,
                    distanceMetres = trajet.summary.distanceTraveled,
                )
                // Fin du suivi (service, position) ; l'écran d'arrivée reste jusqu'à « Terminer ».
                collecte?.cancel()
                collecte = null
                core?.stopNavigation()
                core = null
                _etat.value = resume
            }
            is TripState.Idle -> {
                // Arrêt depuis la notification : on revient à la carte.
                if (_etat.value is EtatGuidage.EnCours) arreter()
            }
        }
    }

    private fun configuration() = NavigationControllerConfig(
        waypointAdvance = WaypointAdvanceMode.WaypointWithinRange(100.0),
        stepAdvanceCondition = stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
        arrivalStepAdvanceCondition = stepAdvanceDistanceToEndOfStep(30u, 32u),
        routeDeviationTracking = RouteDeviationTracking.StaticThreshold(15u, 50.0),
        snappedLocationCourseFiltering = CourseFiltering.SNAP_TO_ROUTE,
    )

    /** Ferrostar exige un client réseau ; le nôtre refuse tout, puisque Sente calcule sur le téléphone. */
    private object SansReseau : HttpClientProvider {
        override suspend fun call(request: RouteRequest): IResponse =
            throw IOException("Sente ne calcule jamais d'itinéraire en ligne.")
    }
}
