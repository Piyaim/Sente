package io.github.piyaim.sente.itineraire

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.Reference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.Route
import uniffi.ferrostar.createRouteFromOsrm
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Préférences de trajet choisies dans l'aperçu (maquette « 6 · Aperçu du trajet »). */
data class OptionsTrajet(val eviterPeages: Boolean = false, val eviterAutoroutes: Boolean = false)

/**
 * Un trajet proposé : la route au format de Ferrostar (pour le guidage), plus ce que l'aperçu affiche.
 * [sansPeage] marque la variante calculée en évitant les péages.
 */
data class Proposition(
    val route: Route,
    val points: List<Reference>,
    val dureeSecondes: Double,
    val distanceMetres: Double,
    val peages: Boolean,
    val autoroute: Boolean,
    val resume: String,
    val sansPeage: Boolean = false,
)

/**
 * Calcul d'itinéraire en voiture, entièrement sur le téléphone, avec Valhalla (valhalla-mobile)
 * et les tuiles de routage de la région. Aucune connexion réseau : aucune adresse de tuiles en ligne
 * n'est configurée, seul le fichier local est lu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoteurItineraire(private val context: Context) {
    // Un seul calcul à la fois, toujours sur le même fil : le moteur natif n'est pas partagé entre fils.
    private val filUnique = Dispatchers.IO.limitedParallelism(1)
    private var moteur: Valhalla? = null
    private var cleMoteur: String? = null

    /**
     * Trajet principal et, s'il passe par des péages, une variante sans péage.
     * Les consignes (affichées et vocales) sont en français.
     */
    suspend fun propositions(tuiles: File, depart: Reference, arrivee: Reference, options: OptionsTrajet): Result<List<Proposition>> =
        withContext(filUnique) {
            try {
                val principale = calculer(tuiles, depart, arrivee, options)
                val liste = mutableListOf(principale)
                if (principale.peages && !options.eviterPeages) {
                    runCatching { calculer(tuiles, depart, arrivee, options.copy(eviterPeages = true)) }.getOrNull()
                        ?.takeIf { !it.peages }
                        ?.let { liste += it.copy(sansPeage = true) }
                }
                Result.success(liste)
            } catch (e: ValhallaException.Internal) {
                Result.failure(IOException(messageErreur(e.message)))
            } catch (e: Exception) {
                Result.failure(IOException("Calcul impossible : ${e.message ?: e.javaClass.simpleName}"))
            }
        }

    /** Un seul trajet (recalcul pendant le guidage). */
    suspend fun route(tuiles: File, depart: Reference, arrivee: Reference, options: OptionsTrajet): Route =
        withContext(filUnique) { calculer(tuiles, depart, arrivee, options).route }

    suspend fun fermer() = withContext(filUnique) {
        moteur?.close()
        moteur = null
        cleMoteur = null
    }

    private fun calculer(tuiles: File, depart: Reference, arrivee: Reference, options: OptionsTrajet): Proposition {
        val reponse = JSONObject(ouvrir(tuiles).routeRaw(requete(depart, arrivee, options)))
        val trajet = reponse.getJSONArray("routes").getJSONObject(0)
        val route = createRouteFromOsrm(
            trajet.toString().toByteArray(),
            reponse.getJSONArray("waypoints").toString().toByteArray(),
            6u,
        )
        val classes = mutableSetOf<String>()
        val resumes = mutableListOf<String>()
        val etapes = trajet.getJSONArray("legs")
        for (i in 0 until etapes.length()) {
            val etape = etapes.getJSONObject(i)
            etape.optString("summary").takeIf { it.isNotBlank() }?.let { resumes += it }
            val pas = etape.getJSONArray("steps")
            for (j in 0 until pas.length()) {
                val intersections = pas.getJSONObject(j).optJSONArray("intersections") ?: continue
                for (k in 0 until intersections.length()) {
                    val c = intersections.getJSONObject(k).optJSONArray("classes") ?: continue
                    for (n in 0 until c.length()) classes += c.getString(n)
                }
            }
        }
        return Proposition(
            route = route,
            points = route.geometry.map { Reference(it.lat, it.lng) },
            dureeSecondes = trajet.getDouble("duration"),
            distanceMetres = trajet.getDouble("distance"),
            peages = "toll" in classes,
            autoroute = "motorway" in classes,
            resume = resumes.joinToString(", "),
        )
    }

    /** Le moteur est long à préparer : on le garde tant que le fichier de tuiles ne change pas. */
    private fun ouvrir(tuiles: File): Valhalla {
        val cle = "${tuiles.absolutePath}|${tuiles.lastModified()}|${tuiles.length()}"
        moteur?.takeIf { cle == cleMoteur }?.let { return it }
        moteur?.close()
        moteur = null
        val config = File(context.filesDir, "valhalla.json")
        config.writeText(configuration(tuiles))
        return Valhalla(config.absolutePath).also {
            moteur = it
            cleMoteur = cle
        }
    }

    /**
     * Réglages par défaut fournis avec valhalla-mobile (ceux de sa version de Valhalla), pointés sur
     * notre fichier. On les écrit nous-mêmes en JSON pour ne pas dépendre de la sérialisation par réflexion.
     */
    private fun configuration(tuiles: File): String {
        val defaut = Valhalla::class.java.getResourceAsStream(CONFIG_PAR_DEFAUT)
            ?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Réglages de Valhalla introuvables dans l'appli.")
        val config = JSONObject(defaut)
        val mjolnir = config.optJSONObject("mjolnir") ?: JSONObject().also { config.put("mjolnir", it) }
        mjolnir.put("tile_extract", tuiles.absolutePath)
        mjolnir.put("tile_dir", File(context.cacheDir, "valhalla").apply { mkdirs() }.absolutePath)
        mjolnir.remove("tile_url")
        mjolnir.put("traffic_extract", "")
        return config.toString()
    }

    /**
     * Requête au format OSRM, avec les consignes affichées (« banner ») et vocales en français :
     * c'est ce que Ferrostar sait lire pour guider.
     */
    private fun requete(depart: Reference, arrivee: Reference, options: OptionsTrajet): String = JSONObject()
        .put(
            "locations",
            JSONArray()
                .put(JSONObject().put("lat", depart.lat).put("lon", depart.lon).put("type", "break"))
                .put(JSONObject().put("lat", arrivee.lat).put("lon", arrivee.lon).put("type", "break")),
        )
        .put("costing", "auto")
        .put(
            "costing_options",
            JSONObject().put(
                "auto",
                JSONObject()
                    .put("use_tolls", if (options.eviterPeages) 0.0 else 0.5)
                    .put("use_highways", if (options.eviterAutoroutes) 0.0 else 1.0),
            ),
        )
        .put("units", "kilometers")
        .put("language", "fr-FR")
        .put("format", "osrm")
        .put("banner_instructions", true)
        .put("voice_instructions", true)
        .toString()

    companion object {
        private const val CONFIG_PAR_DEFAUT = "/com/valhalla/valhalla/default.json"

        /** Une archive tar (les tuiles de routage) : en-tête « ustar » à l'octet 257. */
        fun estTuilesValhalla(fichier: File): Boolean = try {
            RandomAccessFile(fichier, "r").use { f ->
                if (f.length() < 1024) {
                    false
                } else {
                    val octets = ByteArray(5)
                    f.seek(257)
                    f.readFully(octets)
                    octets.decodeToString() == "ustar"
                }
            }
        } catch (e: IOException) {
            false
        }

        /** Codes d'erreur Valhalla les plus courants, en clair. */
        private fun messageErreur(brut: String?): String {
            val code = brut?.let { Regex("code=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            return when (code) {
                171 -> "Aucune route trouvée près du départ ou de l'arrivée (hors de la région installée ?)."
                170, 172, 442 -> "Aucun trajet possible entre ces deux points."
                154 -> "Trajet trop long pour la région installée."
                else -> "Calcul impossible" + (brut?.let { " : $it" } ?: ".")
            }
        }
    }
}

/** Point d'arrivée d'un lieu, pour le calcul. */
fun Lieu.reference(): Reference = Reference(lat, lon)
