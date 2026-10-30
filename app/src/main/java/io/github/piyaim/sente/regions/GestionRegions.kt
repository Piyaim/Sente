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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Catalogue indisponible : ${e.message ?: "erreur réseau"}"
            } finally {
                chargementCatalogue = false
            }
        }
    }

    /** Ce qu'il reste à télécharger pour une région du catalogue, au vu de ce qui est installé. */
    fun aTelecharger(region: RegionCatalogue): List<Tache> {
        val installee = installees.firstOrNull { it.id == region.id }
        val taches = mutableListOf<Tache>()
        if (installee == null || plusRecent(region.carte.donnees, installee.donnees)) {
            taches += Tache(region.id, region.nom, TypeFichier.CARTE, region.carte)
        }
        val recherche = region.recherche
        if (recherche != null && (installee?.recherche == null || plusRecent(recherche.donnees, installee.rechercheDonnees))) {
            taches += Tache(region.id, region.nom, TypeFichier.RECHERCHE, recherche)
        }
        val routage = region.routage
        if (routage != null && (installee?.routage == null || plusRecent(routage.donnees, installee.routageDonnees))) {
            taches += Tache(region.id, region.nom, TypeFichier.ROUTAGE, routage)
        }
        return taches
    }

    fun telecharger(taches: List<Tache>) {
        if (taches.isEmpty()) return
        if (telechargements.enCours() != null) {
            message = "Un téléchargement est déjà en cours."
            return
        }
        try {
            telechargements.lancer(taches)
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
                telechargements.suivre {
                    telechargement = it
                    // La carte est utilisable dès qu'elle est installée, même si la recherche se télécharge encore.
                    if (it is EtatTelechargement.EnCours && it.tache.type != TypeFichier.CARTE && installees.none { r -> r.id == it.tache.regionId }) {
                        installees = depot.installees()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            telechargement = null
            installees = depot.installees()
            resultat
                .onSuccess { tache ->
                    if (affichee == null || idAffichee == null) afficher(tache.regionId)
                    message = "Région installée : ${tache.regionNom}"
                }
                .onFailure { message = it.message ?: "Échec du téléchargement." }
        }
    }

    fun annulerTelechargement() {
        suivi?.cancel()
        telechargements.annuler()
        telechargement = null
        installees = depot.installees()
    }

    fun importer(uri: Uri) {
        if (importEnCours) return
        importEnCours = true
        portee.launch {
            message = try {
                val texte = depot.importer(uri)
                installees = depot.installees()
                if (idAffichee == null) affichee?.let { afficher(it.id) }
                texte
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Import impossible : ${e.message}"
            } finally {
                importEnCours = false
            }
        }
    }

    private fun plusRecent(catalogue: String?, installe: String?): Boolean =
        catalogue != null && (installe == null || catalogue > installe)
}
