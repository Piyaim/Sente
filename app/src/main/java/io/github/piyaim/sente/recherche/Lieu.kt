package io.github.piyaim.sente.recherche

import org.json.JSONObject
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TypeLieu { ADRESSE, RUE, COMMUNE, LIEU, POINT }

/**
 * Un endroit affichable : résultat de recherche, favori, domicile… Les favoris en gardent une copie
 * complète, pour rester valables même si l'index de recherche est remplacé.
 */
data class Lieu(
    val type: TypeLieu,
    val nom: String,
    val sousTitre: String,
    val lat: Double,
    val lon: Double,
    val code: String? = null,
    val libelle: String? = null,
    val adresse: String? = null,
    val horaires: String? = null,
    val telephone: String? = null,
    val site: String? = null,
    val fauteuil: String? = null,
    val osm: String? = null,
) {
    /** Identifie un même endroit d'une recherche à l'autre (favoris, historique). */
    val cle: String get() = "${type.name}|${Normalisation.normaliser(nom)}|${"%.5f".format(Locale.ROOT, lat)}|${"%.5f".format(Locale.ROOT, lon)}"

    fun versJson(): JSONObject = JSONObject().apply {
        put("type", type.name); put("nom", nom); put("sous_titre", sousTitre); put("lat", lat); put("lon", lon)
        code?.let { put("code", it) }; libelle?.let { put("libelle", it) }; adresse?.let { put("adresse", it) }
        horaires?.let { put("horaires", it) }; telephone?.let { put("telephone", it) }; site?.let { put("site", it) }
        fauteuil?.let { put("fauteuil", it) }; osm?.let { put("osm", it) }
    }

    companion object {
        fun depuisJson(j: JSONObject): Lieu? = runCatching {
            fun opt(k: String) = j.optString(k).ifBlank { null }
            Lieu(
                type = TypeLieu.valueOf(j.getString("type")),
                nom = j.getString("nom"),
                sousTitre = j.optString("sous_titre"),
                lat = j.getDouble("lat"),
                lon = j.getDouble("lon"),
                code = opt("code"), libelle = opt("libelle"), adresse = opt("adresse"), horaires = opt("horaires"),
                telephone = opt("telephone"), site = opt("site"), fauteuil = opt("fauteuil"), osm = opt("osm"),
            )
        }.getOrNull()
    }
}

/** Distance à vol d'oiseau, en mètres. */
fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** « 650 m », « 4,8 km », « 37 km ». */
fun distanceLisible(metres: Double): String = when {
    metres < 950 -> "${(metres / 10).toInt() * 10} m"
    metres < 10_000 -> "%.1f km".format(Locale.FRANCE, metres / 1000)
    else -> "${(metres / 1000).toInt()} km"
}
