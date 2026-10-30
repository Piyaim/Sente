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
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Un trajet calculé : tracé, durée, distance, et le temps qu'a pris le calcul (mesure de l'essai 3a). */
data class Trajet(
    val arrivee: Lieu,
    val points: List<Reference>,
    val dureeSecondes: Double,
    val distanceKm: Double,
    val calculMs: Long,
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

    suspend fun calculer(tuiles: File, depart: Reference, arrivee: Lieu): Result<Trajet> = withContext(filUnique) {
        try {
            val debut = System.nanoTime()
            val reponse = ouvrir(tuiles).routeRaw(requete(depart, arrivee))
            val calculMs = (System.nanoTime() - debut) / 1_000_000
            Result.success(lireTrajet(JSONObject(reponse), arrivee, calculMs))
        } catch (e: ValhallaException.Internal) {
            Result.failure(IOException(messageErreur(e.message)))
        } catch (e: Exception) {
            Result.failure(IOException("Calcul impossible : ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun fermer() = withContext(filUnique) {
        moteur?.close()
        moteur = null
        cleMoteur = null
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

    private fun requete(depart: Reference, arrivee: Lieu): String = JSONObject()
        .put(
            "locations",
            JSONArray()
                .put(JSONObject().put("lat", depart.lat).put("lon", depart.lon).put("type", "break"))
                .put(JSONObject().put("lat", arrivee.lat).put("lon", arrivee.lon).put("type", "break")),
        )
        .put("costing", "auto")
        .put("units", "kilometers")
        .put("language", "fr-FR")
        .toString()

    private fun lireTrajet(reponse: JSONObject, arrivee: Lieu, calculMs: Long): Trajet {
        val voyage = reponse.getJSONObject("trip")
        val resume = voyage.getJSONObject("summary")
        val etapes = voyage.getJSONArray("legs")
        val points = ArrayList<Reference>()
        for (i in 0 until etapes.length()) points += decoderPolyligne6(etapes.getJSONObject(i).getString("shape"))
        return Trajet(
            arrivee = arrivee,
            points = points,
            dureeSecondes = resume.getDouble("time"),
            distanceKm = resume.getDouble("length"),
            calculMs = calculMs,
        )
    }

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
