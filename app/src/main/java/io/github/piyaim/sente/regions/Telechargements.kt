package io.github.piyaim.sente.regions

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

sealed interface EtatTelechargement {
    val nom: String

    data class EnCours(override val nom: String, val faits: Long, val total: Long, val enPause: Boolean) : EtatTelechargement
    data class Verification(override val nom: String) : EtatTelechargement
}

/**
 * Téléchargement par le gestionnaire de téléchargements d'Android : il continue écran éteint
 * et reprend après une coupure. Un seul téléchargement à la fois ; il survit à la fermeture de l'appli.
 */
class Telechargements(private val context: Context, private val depot: DepotRegions) {

    private val gestionnaire = context.getSystemService(DownloadManager::class.java)
    private val memoire = context.getSharedPreferences("telechargement", Context.MODE_PRIVATE)

    fun enCours(): Pair<Long, RegionCatalogue>? {
        val id = memoire.getLong(CLE_ID, -1L)
        val region = memoire.getString(CLE_REGION, null)
        if (id < 0 || region == null) return null
        return runCatching { id to RegionCatalogue.depuisJson(JSONObject(region)) }.getOrNull()
    }

    fun lancer(region: RegionCatalogue) {
        val dossier = depot.dossier ?: throw IOException("Stockage de l'appli indisponible.")
        File(dossier, "${region.id}.part").delete()
        val requete = DownloadManager.Request(Uri.parse(region.url))
            .setTitle("Sente : ${region.nom}")
            .setDescription("Carte hors ligne")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DOSSIER_REGIONS, "${region.id}.part")
            .addRequestHeader("User-Agent", AGENT_SENTE)
        val id = gestionnaire.enqueue(requete)
        memoire.edit().putLong(CLE_ID, id).putString(CLE_REGION, region.versJson()).apply()
    }

    fun annuler() {
        val (id, region) = enCours() ?: return
        gestionnaire.remove(id)
        depot.dossier?.let { File(it, "${region.id}.part").delete() }
        oublier()
    }

    /** Suit le téléchargement en cours jusqu'au bout ; renvoie la région installée. */
    suspend fun suivre(maj: (EtatTelechargement) -> Unit): Result<RegionCatalogue> {
        val (id, region) = enCours() ?: return Result.failure(IOException("Aucun téléchargement en cours."))
        while (true) {
            val curseur = gestionnaire.query(DownloadManager.Query().setFilterById(id))
                ?: return echec(region, "Gestionnaire de téléchargements indisponible.")
            curseur.use { c ->
                if (!c.moveToFirst()) return echec(region, "Téléchargement annulé.")
                val statut = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val faits = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    .takeIf { it > 0 } ?: region.taille
                when (statut) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        maj(EtatTelechargement.Verification(region.nom))
                        return finaliser(id, region)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val raison = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        gestionnaire.remove(id)
                        return echec(region, "Échec du téléchargement (code $raison). Vérifiez la connexion et réessayez.")
                    }
                    DownloadManager.STATUS_PAUSED ->
                        maj(EtatTelechargement.EnCours(region.nom, faits, total, enPause = true))
                    else ->
                        maj(EtatTelechargement.EnCours(region.nom, faits, total, enPause = false))
                }
            }
            delay(700)
        }
    }

    /** Vérifie taille, empreinte et format avant d'installer : un fichier abîmé n'est jamais affiché. */
    private suspend fun finaliser(id: Long, region: RegionCatalogue): Result<RegionCatalogue> = withContext(Dispatchers.IO) {
        val dossier = depot.dossier
        val partiel = dossier?.let { File(it, "${region.id}.part") }
        try {
            if (dossier == null || partiel == null || !partiel.exists()) throw IOException("Fichier téléchargé introuvable.")
            if (partiel.length() != region.taille) throw IOException("Taille inattendue : fichier incomplet, réessayez.")
            if (!DepotRegions.sha256(partiel).equals(region.sha256, ignoreCase = true)) {
                throw IOException("Empreinte incorrecte : fichier abîmé, réessayez.")
            }
            Pmtiles.lireEnTete(partiel) ?: throw IOException("Le fichier reçu n'est pas une carte valide.")
            val destination = File(dossier, "${region.id}.pmtiles")
            destination.delete()
            if (!partiel.renameTo(destination)) throw IOException("Impossible d'installer la carte.")
            depot.ecrireMeta(region.id, region.nom, region.donnees, "catalogue")
            Result.success(region)
        } catch (e: IOException) {
            partiel?.delete()
            Result.failure(e)
        } finally {
            gestionnaire.remove(id)
            oublier()
        }
    }

    private fun echec(region: RegionCatalogue, message: String): Result<RegionCatalogue> {
        depot.dossier?.let { File(it, "${region.id}.part").delete() }
        oublier()
        return Result.failure(IOException(message))
    }

    private fun oublier() {
        memoire.edit().remove(CLE_ID).remove(CLE_REGION).apply()
    }

    private companion object {
        const val CLE_ID = "id"
        const val CLE_REGION = "region"
    }
}
