package io.github.piyaim.sente.regions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Catalogue publié par le dépôt Sente-cartes (adresse affichée dans l'appli). */
const val URL_CATALOGUE = "https://github.com/Piyaim/Sente-cartes/releases/download/regions/catalogue.json"

/** En-tête « User-Agent » neutre : sans lui, Android enverrait le modèle du téléphone. */
const val AGENT_SENTE = "Sente"

data class FichierCatalogue(val url: String, val taille: Long, val sha256: String, val donnees: String?)

data class RegionCatalogue(
    val id: String,
    val nom: String,
    val carte: FichierCatalogue,
    val zoomMax: Int,
    val recherche: FichierCatalogue?,
) {
    val tailleTotale: Long get() = carte.taille + (recherche?.taille ?: 0L)

    companion object {
        fun depuisJson(json: JSONObject): RegionCatalogue {
            val recherche = json.optJSONObject("recherche")?.let {
                FichierCatalogue(it.getString("url"), it.getLong("taille"), it.getString("sha256"), it.optString("donnees").ifBlank { null })
            }
            return RegionCatalogue(
                id = json.getString("id"),
                nom = json.getString("nom"),
                carte = FichierCatalogue(json.getString("url"), json.getLong("taille"), json.getString("sha256"), json.optString("donnees").ifBlank { null }),
                zoomMax = json.optInt("zoom_max", 14),
                recherche = recherche?.takeIf { it.url.startsWith("https://") },
            )
        }
    }
}

object Catalogue {
    suspend fun charger(): List<RegionCatalogue> = withContext(Dispatchers.IO) {
        val connexion = URL(URL_CATALOGUE).openConnection() as HttpURLConnection
        connexion.connectTimeout = 15_000
        connexion.readTimeout = 20_000
        connexion.instanceFollowRedirects = true
        connexion.setRequestProperty("User-Agent", AGENT_SENTE)
        try {
            if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Le serveur a répondu ${connexion.responseCode}.")
            }
            val texte = connexion.inputStream.bufferedReader().use { it.readText() }
            val regions = JSONObject(texte).getJSONArray("regions")
            (0 until regions.length())
                .map { RegionCatalogue.depuisJson(regions.getJSONObject(it)) }
                .filter { DepotRegions.idValide(it.id) && it.carte.url.startsWith("https://") }
        } finally {
            connexion.disconnect()
        }
    }
}
