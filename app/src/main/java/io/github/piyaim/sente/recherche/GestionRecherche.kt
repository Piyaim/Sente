package io.github.piyaim.sente.recherche

import io.github.piyaim.sente.regions.RegionInstallee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Accès à l'index de recherche de la région affichée. Toutes les lectures passent par un seul fil,
 * ce qui évite de fermer l'index pendant une recherche quand la région change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GestionRecherche {
    private val filUnique = Dispatchers.IO.limitedParallelism(1)
    private var index: IndexRecherche? = null
    private var cleIndex: String? = null

    suspend fun chercher(region: RegionInstallee?, texte: String, ref: Reference): Resultats? = withContext(filUnique) {
        ouvrir(region)?.chercher(texte, ref)
    }

    suspend fun parCategorie(region: RegionInstallee?, code: String, ref: Reference): List<Lieu>? = withContext(filUnique) {
        ouvrir(region)?.parCategorie(code, ref)
    }

    suspend fun fermer() = withContext(filUnique) {
        index?.close()
        index = null
        cleIndex = null
    }

    private fun ouvrir(region: RegionInstallee?): IndexRecherche? {
        val fichier = region?.recherche
        val cle = fichier?.let { "${it.absolutePath}|${it.lastModified()}|${it.length()}" }
        if (cle != cleIndex) {
            index?.close()
            index = fichier?.let { IndexRecherche.ouvrir(it) }
            cleIndex = cle
        }
        return index
    }
}

/** Catégories proposées en un appui dans la recherche (code OSM utilisé par l'index → libellé). */
val CATEGORIES_RAPIDES = listOf(
    "bakery" to "Boulangerie",
    "fuel" to "Carburant",
    "charging_station" to "Recharge",
    "parking" to "Parking",
    "pharmacy" to "Pharmacie",
    "supermarket" to "Supermarché",
    "restaurant" to "Restaurant",
)
