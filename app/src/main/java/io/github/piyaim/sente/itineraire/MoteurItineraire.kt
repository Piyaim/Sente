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
data class OptionsTrajet(
    val eviterPeages: Boolean = false,
    val eviterAutoroutes: Boolean = false,
    /** Points à contourner (sections de péage écartées d'un appui dans le détail des péages). */
    val exclusions: List<Reference> = emptyList(),
)

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
    /** Cabines de péage traversées, dans l'ordre ; null si Valhalla n'a pas pu les retrouver. */
    val passages: List<PassagePeage>? = emptyList(),
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
        val valhalla = ouvrir(tuiles)
        val reponse = JSONObject(valhalla.routeRaw(requete(depart, arrivee, options)))
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
            passages = if ("toll" in classes) runCatching { passages(valhalla, trajet.getString("geometry")) }.getOrNull() else emptyList(),
        )
    }

    /**
     * Cabines de péage réellement traversées : Valhalla parcourt le tracé (« trace_attributes ») et
     * signale les nœuds de type cabine. Plus fiable que chercher les gares près du tracé, qui
     * compterait aussi les bretelles voisines.
     */
    private fun passages(valhalla: Valhalla, geometrie: String): List<PassagePeage> {
        val requete = JSONObject()
            .put("encoded_polyline", geometrie)
            .put("shape_match", "edge_walk")
            .put("costing", "auto")
            .put(
                "filters",
                JSONObject()
                    .put("action", "include")
                    .put("attributes", JSONArray(listOf("edge.end_shape_index", "edge.length", "edge.names", "node.type", "shape"))),
            )
        val reponse = JSONObject(valhalla.traceAttributesRaw(requete.toString()))
        val forme = decoderPolyligne6(reponse.getString("shape"))
        val aretes = reponse.getJSONArray("edges")
        val resultat = mutableListOf<PassagePeage>()
        var metres = 0.0
        for (i in 0 until aretes.length()) {
            val arete = aretes.getJSONObject(i)
            metres += arete.optDouble("length", 0.0) * 1000 // kilomètres
            val type = arete.optJSONObject("end_node")?.optString("type")
            if (type != "toll_booth" && type != "toll_gantry") continue
            val point = forme.getOrNull(arete.optInt("end_shape_index", -1)) ?: continue
            val noms = arete.optJSONArray("names")
            val route = (0 until (noms?.length() ?: 0)).map { noms!!.getString(it) }.firstOrNull { AUTOROUTE.matches(it) }
            resultat += PassagePeage(point.lat, point.lon, metres, route?.replace(" ", ""))
        }
        return resultat
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
        .apply {
            if (options.exclusions.isNotEmpty()) {
                // Rayon de 150 m : une gare a plusieurs voies parallèles, toutes doivent être écartées.
                put("exclude_locations", JSONArray(options.exclusions.map { JSONObject().put("lat", it.lat).put("lon", it.lon).put("radius", 150) }))
            }
        }
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
        private val AUTOROUTE = Regex("^A ?\\d+[A-Z]?$")

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

/** Décode une polyligne encodée avec 6 décimales (format des tracés Valhalla). */
fun decoderPolyligne6(code: String): List<Reference> {
    val points = ArrayList<Reference>()
    var i = 0
    var lat = 0L
    var lon = 0L
    while (i < code.length) {
        for (axe in 0..1) {
            var resultat = 0L
            var decalage = 0
            var b: Int
            do {
                b = code[i++].code - 63
                resultat = resultat or ((b and 0x1f).toLong() shl decalage)
                decalage += 5
            } while (b >= 0x20)
            val delta = if ((resultat and 1L) != 0L) (resultat shr 1).inv() else resultat shr 1
            if (axe == 0) lat += delta else lon += delta
        }
        points += Reference(lat / 1e6, lon / 1e6)
    }
    return points
}
