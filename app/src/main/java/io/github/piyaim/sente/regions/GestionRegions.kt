package io.github.piyaim.sente.regions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.piyaim.sente.Reglages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** État partagé par la carte et l'écran des régions. Aucun accès réseau sans action de l'utilisateur. */
class GestionRegions(context: Context, private val portee: CoroutineScope) {

    private val depot = DepotRegions(context)
    private val telechargements = Telechargements(context, depot)
    private val reglages = Reglages(context)

    var installees by mutableStateOf(depot.installees())
        private set
    var catalogue by mutableStateOf<List<RegionCatalogue>?>(null)
        private set
    var chargementCatalogue by mutableStateOf(false)
        private set
    var telechargement by mutableStateOf<EtatTelechargement?>(null)
        private set
    var importEnCours by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var fournisseurReseau by mutableStateOf(reglages.fournisseurReseau)
        private set
    private var idAffichee by mutableStateOf(reglages.regionAffichee)

    private var suivi: Job? = null

    /** Région dessinée sur la carte : celle choisie, sinon la première installée. */
    val affichee: RegionInstallee?
        get() = installees.firstOrNull { it.id == idAffichee } ?: installees.firstOrNull()

    fun afficher(id: String) {
        idAffichee = id
        reglages.regionAffichee = id
    }

    fun supprimer(id: String) {
        if (idAffichee == id) {
            idAffichee = null
            reglages.regionAffichee = null
        }
        depot.supprimer(id)
        installees = depot.installees()
    }

    fun definirFournisseurReseau(actif: Boolean) {
        reglages.fournisseurReseau = actif
        fournisseurReseau = actif
    }

    fun chargerCatalogue() {
        if (chargementCatalogue) return
        chargementCatalogue = true
        portee.launch {
            try {
                catalogue = Catalogue.charger()
            } catch (e: Exception) {
                message = "Catalogue indisponible : ${e.message ?: "erreur réseau"}"
            } finally {
                chargementCatalogue = false
            }
        }
    }

    fun telecharger(region: RegionCatalogue) {
        if (telechargements.enCours() != null) {
            message = "Un téléchargement est déjà en cours."
            return
        }
        try {
            telechargements.lancer(region)
        } catch (e: Exception) {
            message = "Téléchargement impossible : ${e.message}"
            return
        }
        suivreTelechargement()
    }

    /** À l'ouverture de l'appli : reprend le suivi d'un téléchargement lancé avant. */
    fun suivreTelechargement() {
        if (telechargements.enCours() == null || suivi?.isActive == true) return
        suivi = portee.launch {
            val resultat = try {
                telechargements.suivre { telechargement = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            telechargement = null
            installees = depot.installees()
            resultat
                .onSuccess { region ->
                    afficher(region.id)
                    message = "Carte installée : ${region.nom}"
                }
                .onFailure { message = it.message ?: "Échec du téléchargement." }
        }
    }

    fun annulerTelechargement() {
        suivi?.cancel()
        telechargements.annuler()
        telechargement = null
    }

    fun importer(uri: Uri) {
        if (importEnCours) return
        importEnCours = true
        portee.launch {
            message = try {
                val region = depot.importer(uri)
                installees = depot.installees()
                afficher(region.id)
                "Carte importée : ${region.nom}"
            } catch (e: Exception) {
                "Import impossible : ${e.message}"
            } finally {
                importEnCours = false
            }
        }
    }
}
