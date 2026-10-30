package io.github.piyaim.sente.regions

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import io.github.piyaim.sente.itineraire.MoteurItineraire
import io.github.piyaim.sente.recherche.IndexRecherche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Un fichier à télécharger pour une région. */
data class Tache(
    val regionId: String,
    val regionNom: String,
    val type: TypeFichier,
    val fichier: FichierCatalogue,
) {
    fun versJson(): JSONObject = JSONObject()
        .put("region_id", regionId).put("region_nom", regionNom).put("type", type.name)
        .put("url", fichier.url).put("taille", fichier.taille).put("sha256", fichier.sha256)
        .put("donnees", fichier.donnees ?: "")

    companion object {
        fun depuisJson(j: JSONObject) = Tache(
            regionId = j.getString("region_id"),
            regionNom = j.getString("region_nom"),
            type = TypeFichier.valueOf(j.getString("type")),
            fichier = FichierCatalogue(j.getString("url"), j.getLong("taille"), j.getString("sha256"), j.optString("donnees").ifBlank { null }),
        )
    }
}

sealed interface EtatTelechargement {
    val tache: Tache
    val etape: Int
    val etapes: Int

    data class EnCours(
        override val tache: Tache,
        override val etape: Int,
        override val etapes: Int,
        val faits: Long,
        val total: Long,
        val enPause: Boolean,
    ) : EtatTelechargement

    data class Verification(override val tache: Tache, override val etape: Int, override val etapes: Int) : EtatTelechargement
}

/**
 * Téléchargements par le gestionnaire de téléchargements d'Android : ils continuent écran éteint et reprennent
 * après une coupure. Une file à la fois (la carte puis la recherche d'une région) ; elle survit à la fermeture de l'appli.
 */
class Telechargements(private val context: Context, private val depot: DepotRegions) {

    private val gestionnaire = context.getSystemService(DownloadManager::class.java)
    private val memoire = context.getSharedPreferences("telechargement", Context.MODE_PRIVATE)

    fun enCours(): Pair<Long, List<Tache>>? {
        val id = memoire.getLong(CLE_ID, -1L)
        val file = memoire.getString(CLE_FILE, null)
        if (id < 0 || file == null) return null
        return runCatching {
            val taches = JSONArray(file).let { a -> (0 until a.length()).map { Tache.depuisJson(a.getJSONObject(it)) } }
            if (taches.isEmpty()) null else id to taches
        }.getOrNull()
    }

    fun lancer(taches: List<Tache>) {
        if (taches.isEmpty()) return
        memoire.edit().putInt(CLE_ETAPES, taches.size).apply()
        demarrer(taches)
    }

    private fun demarrer(taches: List<Tache>) {
        val tache = taches.first()
        val partiel = partiel(tache) ?: throw IOException("Stockage de l'appli indisponible.")
        partiel.delete()
        val requete = DownloadManager.Request(Uri.parse(tache.fichier.url))
            .setTitle("Sente : ${tache.regionNom}")
            .setDescription(
                when (tache.type) {
                    TypeFichier.CARTE -> "Carte hors ligne"
                    TypeFichier.RECHERCHE -> "Recherche hors ligne"
                    TypeFichier.ROUTAGE -> "Itinéraire hors ligne"
                },
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DOSSIER_REGIONS, partiel.name)
            .addRequestHeader("User-Agent", AGENT_SENTE)
        val id = gestionnaire.enqueue(requete)
        memoire.edit()
            .putLong(CLE_ID, id)
            .putString(CLE_FILE, JSONArray().apply { taches.forEach { put(it.versJson()) } }.toString())
            .apply()
    }

    fun annuler() {
        val (id, taches) = enCours() ?: return
        gestionnaire.remove(id)
        partiel(taches.first())?.delete()
        oublier()
    }

    /** Suit la file jusqu'au bout ; renvoie la dernière tâche terminée. */
    suspend fun suivre(maj: (EtatTelechargement) -> Unit): Result<Tache> {
        while (true) {
            val (id, taches) = enCours() ?: return Result.failure(IOException("Aucun téléchargement en cours."))
            val tache = taches.first()
            val etapes = memoire.getInt(CLE_ETAPES, taches.size).coerceAtLeast(taches.size)
            val etape = etapes - taches.size + 1
            val ligne = lireLigne(id, tache) ?: return echec(tache, "Téléchargement annulé.")
            when (ligne.statut) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    maj(EtatTelechargement.Verification(tache, etape, etapes))
                    val resultat = finaliser(tache)
                    // Après le déplacement du fichier seulement : « remove » efface le fichier téléchargé s'il est encore là.
                    gestionnaire.remove(id)
                    if (resultat.isFailure) {
                        oublier()
                        return resultat
                    }
                    val restantes = taches.drop(1)
                    if (restantes.isEmpty()) {
                        oublier()
                        return resultat
                    }
                    try {
                        demarrer(restantes)
                    } catch (e: Exception) {
                        oublier()
                        return Result.failure(IOException("Impossible de lancer la suite : ${e.message}"))
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    gestionnaire.remove(id)
                    return echec(tache, "Échec du téléchargement (code ${ligne.raison}). Vérifiez la connexion et réessayez.")
                }
                DownloadManager.STATUS_PAUSED -> maj(EtatTelechargement.EnCours(tache, etape, etapes, ligne.faits, ligne.total, enPause = true))
                else -> maj(EtatTelechargement.EnCours(tache, etape, etapes, ligne.faits, ligne.total, enPause = false))
            }
            delay(700)
        }
    }

    private class Ligne(val statut: Int, val faits: Long, val total: Long, val raison: Int)

    private fun lireLigne(id: Long, tache: Tache): Ligne? {
        val curseur = gestionnaire.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return curseur.use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                Ligne(
                    statut = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    faits = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: tache.fichier.taille,
                    raison = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                )
            }
        }
    }

    /** Vérifie taille, empreinte et format avant d'installer : un fichier abîmé n'est jamais utilisé. */
    private suspend fun finaliser(tache: Tache): Result<Tache> = withContext(Dispatchers.IO) {
        val partiel = partiel(tache)
        try {
            val destination = depot.fichier(tache.regionId, tache.type)
            if (partiel == null || destination == null || !partiel.exists()) throw IOException("Fichier téléchargé introuvable.")
            if (partiel.length() != tache.fichier.taille) throw IOException("Taille inattendue : fichier incomplet, réessayez.")
            if (!DepotRegions.sha256(partiel).equals(tache.fichier.sha256, ignoreCase = true)) {
                throw IOException("Empreinte incorrecte : fichier abîmé, réessayez.")
            }
            val valide = when (tache.type) {
                TypeFichier.CARTE -> Pmtiles.lireEnTete(partiel) != null
                TypeFichier.RECHERCHE -> IndexRecherche.lireMeta(partiel)?.get("region") == tache.regionId
                TypeFichier.ROUTAGE -> MoteurItineraire.estTuilesValhalla(partiel)
            }
            if (!valide) throw IOException("Le fichier reçu n'est pas valide.")
            destination.delete()
            if (!partiel.renameTo(destination)) throw IOException("Impossible d'installer le fichier.")
            when (tache.type) {
                TypeFichier.CARTE -> depot.majMeta(tache.regionId, nom = tache.regionNom, donnees = tache.fichier.donnees, origine = "catalogue")
                TypeFichier.RECHERCHE -> depot.majMeta(tache.regionId, nom = tache.regionNom, rechercheDonnees = tache.fichier.donnees)
                TypeFichier.ROUTAGE -> depot.majMeta(tache.regionId, nom = tache.regionNom, routageDonnees = tache.fichier.donnees)
            }
            Result.success(tache)
        } catch (e: IOException) {
            partiel?.delete()
            Result.failure(e)
        }
    }

    private fun partiel(tache: Tache): File? = depot.fichier(tache.regionId, tache.type)?.let { File(it.parentFile, it.name + ".part") }

    private fun echec(tache: Tache, message: String): Result<Tache> {
        partiel(tache)?.delete()
        oublier()
        return Result.failure(IOException(message))
    }

    private fun oublier() {
        memoire.edit().remove(CLE_ID).remove(CLE_FILE).remove(CLE_ETAPES).apply()
    }

    private companion object {
        const val CLE_ID = "id"
        const val CLE_FILE = "file"
        const val CLE_ETAPES = "etapes"
    }
}
