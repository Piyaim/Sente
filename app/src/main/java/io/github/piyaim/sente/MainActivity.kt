package io.github.piyaim.sente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.piyaim.sente.carte.CarteEcran
import io.github.piyaim.sente.itineraire.BandeTrajet
import io.github.piyaim.sente.itineraire.MoteurItineraire
import io.github.piyaim.sente.itineraire.Trajet
import io.github.piyaim.sente.perso.ChoixListe
import io.github.piyaim.sente.perso.DonneesPerso
import io.github.piyaim.sente.perso.FavorisEcran
import io.github.piyaim.sente.perso.Raccourci
import io.github.piyaim.sente.recherche.CarteCategorie
import io.github.piyaim.sente.recherche.FicheLieu
import io.github.piyaim.sente.recherche.GestionRecherche
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.RechercheEcran
import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.recherche.VueCategorie
import io.github.piyaim.sente.regions.GestionRegions
import io.github.piyaim.sente.regions.RegionsEcran
import io.github.piyaim.sente.ui.SenteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SenteTheme { AppSente() } }
    }
}

private enum class Ecran { CARTE, RECHERCHE, FAVORIS, REGLAGES }

@Composable
private fun AppSente() {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val gestion = remember { GestionRegions(context.applicationContext, portee) }
    val recherche = remember { GestionRecherche() }
    val perso = remember { DonneesPerso(context.applicationContext) }
    val moteur = remember { MoteurItineraire(context.applicationContext) }
    val messages = remember { SnackbarHostState() }

    var ecran by rememberSaveable { mutableStateOf(Ecran.CARTE) }
    var selectionJson by rememberSaveable { mutableStateOf<String?>(null) }
    val selection = remember(selectionJson) { selectionJson?.let { Lieu.depuisJson(JSONObject(it)) } }
    // Résultats d'une catégorie affichés sur la carte (« Boulangerie »…), gardés comme la sélection.
    var categorieJson by rememberSaveable { mutableStateOf<String?>(null) }
    val categorie = remember(categorieJson) { categorieJson?.let { VueCategorie.depuisJson(it) } }
    var positionAutorisee by remember { mutableStateOf(positionPreciseAccordee(context)) }
    var position by remember { mutableStateOf<Reference?>(null) }
    var centre by remember { mutableStateOf<Reference?>(null) }
    var versionPerso by remember { mutableStateOf(0) } // relit favoris et historique après une modification
    var choixListePour by remember { mutableStateOf<Lieu?>(null) }
    // Essai 3a : trajet calculé sur le téléphone, affiché sur la carte (pas encore de guidage).
    var trajet by remember { mutableStateOf<Trajet?>(null) }
    var calculEnCours by remember { mutableStateOf(false) }

    val historique = remember(versionPerso) { perso.historique() }
    val listes = remember(versionPerso) { perso.listes() }
    val raccourcis = remember(versionPerso) { Raccourci.entries.associateWith { perso.raccourci(it) } }
    val favori = remember(versionPerso, selection) { selection?.let { perso.estFavori(it) } ?: false }
    val reference = position ?: centre

    fun afficherMessage(texte: String) {
        portee.launch { messages.showSnackbar(texte) }
    }

    fun choisir(lieu: Lieu) {
        perso.ajouterHistorique(lieu)
        versionPerso++
        selectionJson = lieu.versJson().toString()
        ecran = Ecran.CARTE
        // Ouvert depuis une catégorie : ce lieu devient celui mis en avant quand la fiche se ferme.
        categorie?.let { vue ->
            val i = vue.lieux.indexOfFirst { it.cle == lieu.cle }
            if (i >= 0) categorieJson = vue.copy(index = i).versJson()
        }
    }

    fun mettreEnAvant(i: Int) {
        val vue = categorie ?: return
        categorieJson = vue.copy(index = i.coerceIn(0, vue.lieux.lastIndex)).versJson()
    }

    fun ouvrirCategorie(code: String?, libelle: String) {
        if (code == null) {
            categorieJson = null
            return
        }
        val region = gestion.affichee
        val ref = reference ?: region?.let { Reference(it.enTete.latCentre, it.enTete.lonCentre) } ?: return
        portee.launch {
            val lieux = recherche.parCategorie(region, code, ref).orEmpty()
            if (lieux.isEmpty()) {
                afficherMessage("Aucun résultat « $libelle » dans cette région.")
            } else {
                categorieJson = VueCategorie(code, libelle, lieux).versJson()
                selectionJson = null
                ecran = Ecran.CARTE
            }
        }
    }

    fun calculerItineraire(vers: Lieu) {
        val tuiles = gestion.affichee?.routage
        val depart = position
        when {
            tuiles == null -> afficherMessage("Itinéraire non installé : « Cartes et réglages » → « Ajouter l'itinéraire ».")
            depart == null -> afficherMessage("Position inconnue : attendez le signal GPS pour calculer un trajet.")
            calculEnCours -> Unit
            else -> {
                calculEnCours = true
                afficherMessage("Calcul de l'itinéraire…")
                portee.launch {
                    val resultat = moteur.calculer(tuiles, depart, vers)
                    calculEnCours = false
                    resultat
                        .onSuccess {
                            messages.currentSnackbarData?.dismiss()
                            trajet = it
                            selectionJson = null
                        }
                        .onFailure { afficherMessage(it.message ?: "Calcul impossible.") }
                }
            }
        }
    }

    val demandePosition = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        positionAutorisee = positionPreciseAccordee(context)
    }
    val demander = {
        demandePosition.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        gestion.suivreTelechargement()
        if (!positionAutorisee) demander()
    }

    BackHandler(enabled = ecran != Ecran.CARTE || selection != null || trajet != null || categorie != null) {
        when {
            ecran != Ecran.CARTE -> ecran = Ecran.CARTE
            selection != null -> selectionJson = null
            trajet != null -> trajet = null
            else -> categorieJson = null
        }
    }

    val trajetAffiche = trajet
    val bandeauBas: (@Composable () -> Unit)? = when {
        trajetAffiche != null -> @Composable { BandeTrajet(trajetAffiche, onFermer = { trajet = null }) }
        categorie != null -> @Composable {
            CarteCategorie(
                vue = categorie,
                reference = position,
                onFiche = { choisir(categorie.choisi) },
                onItineraire = { calculerItineraire(categorie.choisi) },
                onIndex = ::mettreEnAvant,
            )
        }
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        // La carte reste en place sous les autres écrans : pas de rechargement en revenant.
        CarteEcran(
            region = gestion.affichee,
            sombre = isSystemInDarkTheme(),
            positionAutorisee = positionAutorisee,
            utiliserReseau = gestion.fournisseurReseau,
            selection = selection,
            onDemanderPosition = demander,
            onOuvrirRegions = { ecran = Ecran.REGLAGES },
            onRechercher = { ecran = Ecran.RECHERCHE },
            onRaccourci = { r ->
                val lieu = raccourcis[r]
                if (lieu != null) {
                    selectionJson = lieu.versJson().toString()
                } else {
                    afficherMessage("${r.libelle} non défini : ouvrez une adresse puis « Enregistrer comme ${r.libelle} ».")
                }
            },
            onFavoris = { ecran = Ecran.FAVORIS },
            onPosition = { position = Reference(it.latitude, it.longitude) },
            onCentre = { lat, lon -> centre = Reference(lat, lon) },
            categorie = categorie,
            onPointCategorie = { i ->
                val vue = categorie
                // Point déjà mis en avant, ou fiche ouverte : on ouvre la fiche de ce point.
                if (vue != null && i in vue.lieux.indices) {
                    if (selection != null || i == vue.index) choisir(vue.lieux[i]) else mettreEnAvant(i)
                }
            },
            onListeCategorie = { ecran = Ecran.RECHERCHE },
            onFermerCategorie = { categorieJson = null },
            trajet = trajet,
            bandeauBas = bandeauBas,
        )

        val lieu = selection
        if (lieu != null && ecran == Ecran.CARTE) {
            FicheLieu(
                lieu = lieu,
                reference = position,
                favori = favori,
                onFermer = { selectionJson = null },
                onItineraire = { calculerItineraire(lieu) },
                onFavori = {
                    if (favori) {
                        perso.retirerFavori(lieu)
                        versionPerso++
                        afficherMessage("Retiré des favoris")
                    } else {
                        choixListePour = lieu
                    }
                },
                onRaccourci = { r ->
                    perso.definirRaccourci(r, lieu)
                    versionPerso++
                    afficherMessage("${r.libelle} : ${lieu.nom}")
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        when (ecran) {
            Ecran.CARTE -> Unit
            Ecran.RECHERCHE -> Surface(Modifier.fillMaxSize()) {
                RechercheEcran(
                    region = gestion.affichee,
                    recherche = recherche,
                    reference = reference,
                    historique = historique,
                    categorie = categorie,
                    onCategorie = ::ouvrirCategorie,
                    onChoisir = ::choisir,
                    onEffacerHistorique = {
                        perso.effacerHistorique()
                        versionPerso++
                    },
                    onRetour = { ecran = Ecran.CARTE },
                    onOuvrirRegions = { ecran = Ecran.REGLAGES },
                )
            }
            Ecran.FAVORIS -> Surface(Modifier.fillMaxSize()) {
                FavorisEcran(
                    listes = listes,
                    raccourcis = raccourcis,
                    reference = reference,
                    onChoisir = ::choisir,
                    onRetirer = { l, liste ->
                        perso.retirerDeListe(l, liste)
                        versionPerso++
                    },
                    onOublierRaccourci = { r ->
                        perso.definirRaccourci(r, null)
                        versionPerso++
                    },
                    onRetour = { ecran = Ecran.CARTE },
                )
            }
            Ecran.REGLAGES -> Surface(Modifier.fillMaxSize()) {
                RegionsEcran(
                    gestion = gestion,
                    onExporter = { uri ->
                        portee.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openOutputStream(uri)?.use { it.write(perso.exporterJson().toByteArray()) }
                                        ?: error("fichier inaccessible")
                                }.isSuccess
                            }
                            gestion.message = if (ok) "Données exportées" else "Export impossible"
                        }
                    },
                    onToutEffacer = {
                        perso.toutEffacer()
                        versionPerso++
                        gestion.message = "Favoris, domicile, travail et historique effacés"
                    },
                    onRetour = { ecran = Ecran.CARTE },
                )
            }
        }

        SnackbarHost(messages, Modifier.align(Alignment.TopCenter).safeDrawingPadding())
    }

    choixListePour?.let { lieu ->
        ChoixListe(
            nomsExistants = perso.nomsDeListes(),
            onChoisir = { liste ->
                perso.ajouterFavori(lieu, liste)
                versionPerso++
                choixListePour = null
                afficherMessage("Enregistré dans « $liste »")
            },
            onAnnuler = { choixListePour = null },
        )
    }
}

/** Le GPS exige la position précise ; la position approximative seule ne suffit pas. */
private fun positionPreciseAccordee(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
