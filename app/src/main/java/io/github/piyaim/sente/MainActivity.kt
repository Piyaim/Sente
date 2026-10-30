package io.github.piyaim.sente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.collectAsState
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
import io.github.piyaim.sente.carte.AffichageTrajet
import io.github.piyaim.sente.guidage.ArriveeCarte
import io.github.piyaim.sente.guidage.EtatGuidage
import io.github.piyaim.sente.guidage.GestionGuidage
import io.github.piyaim.sente.guidage.GuidageEcran
import io.github.piyaim.sente.itineraire.ApercuTrajet
import io.github.piyaim.sente.itineraire.EtatApercu
import io.github.piyaim.sente.itineraire.MoteurItineraire
import io.github.piyaim.sente.itineraire.reference
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
    val guidage = remember { GestionGuidage(context.applicationContext, moteur) }
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
    // Aperçu du trajet avant de démarrer, puis guidage (Ferrostar) et arrivée.
    var apercu by remember { mutableStateOf<EtatApercu?>(null) }
    val etatGuidage by guidage.etat.collectAsState()
    val muet by guidage.muet.collectAsState()
    var suivi by remember { mutableStateOf(true) }
    var recentrage by remember { mutableStateOf(0) }
    var demarrageEnAttente by remember { mutableStateOf<Boolean?>(null) } // true : simulation

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

    /** Calcule les trajets de l'aperçu ; relancé quand une option change. */
    fun calculerApercu(etat: EtatApercu) {
        val tuiles = gestion.affichee?.routage
        val depart = position
        when {
            tuiles == null -> afficherMessage("Itinéraire non installé : « Cartes et réglages » → « Ajouter l'itinéraire ».")
            depart == null -> afficherMessage("Position inconnue : attendez le signal GPS pour calculer un trajet.")
            else -> {
                apercu = etat.copy(propositions = null, choisie = 0, erreur = null)
                selectionJson = null
                portee.launch {
                    val resultat = moteur.propositions(tuiles, depart, etat.destination.reference(), etat.options)
                    // Ignoré si l'aperçu a été fermé ou les options changées entre-temps.
                    val courant = apercu ?: return@launch
                    if (courant.destination != etat.destination || courant.options != etat.options) return@launch
                    apercu = resultat.fold(
                        onSuccess = { courant.copy(propositions = it, erreur = null) },
                        onFailure = { courant.copy(propositions = emptyList(), erreur = it.message ?: "Calcul impossible.") },
                    )
                }
            }
        }
    }

    fun calculerItineraire(vers: Lieu) = calculerApercu(EtatApercu(vers))

    fun lancerGuidage(simuler: Boolean) {
        val etat = apercu ?: return
        val proposition = etat.proposition ?: return
        val tuiles = gestion.affichee?.routage ?: return
        suivi = true
        guidage.demarrer(proposition, etat.destination, tuiles, etat.options, simuler)
        apercu = null
    }

    val demandePosition = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        positionAutorisee = positionPreciseAccordee(context)
    }
    // Notifications (Android 13 et plus) : demandées au premier guidage ; un refus n'empêche pas de guider.
    val demandeNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        demarrageEnAttente?.let { lancerGuidage(it) }
        demarrageEnAttente = null
    }
    fun demarrer(simuler: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            demarrageEnAttente = simuler
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            lancerGuidage(simuler)
        }
    }
    val demander = {
        demandePosition.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        gestion.suivreTelechargement()
        if (!positionAutorisee) demander()
    }

    val enGuidage = etatGuidage is EtatGuidage.EnCours
    BackHandler(enabled = ecran != Ecran.CARTE || selection != null || etatGuidage != null || apercu != null || categorie != null) {
        when {
            ecran != Ecran.CARTE -> ecran = Ecran.CARTE
            // Pendant un trajet, le retour ne coupe pas le guidage par erreur.
            enGuidage -> afficherMessage("Touchez ✕ pour arrêter le guidage.")
            etatGuidage != null -> guidage.arreter()
            selection != null -> selectionJson = null
            apercu != null -> apercu = null
            else -> categorieJson = null
        }
    }

    // Ce que la carte dessine : trajet suivi pendant le guidage, ou trajet choisi et variante dans l'aperçu.
    val g = etatGuidage
    val a = apercu
    val affichageTrajet = when {
        g is EtatGuidage.EnCours -> AffichageTrajet(g.trace, null, g.destination, guidage = true)
        g is EtatGuidage.Arrive -> AffichageTrajet(emptyList(), null, g.destination, guidage = false)
        a != null -> AffichageTrajet(
            trace = a.proposition?.points.orEmpty(),
            variante = a.propositions?.filterIndexed { i, _ -> i != a.choisie }?.firstOrNull()?.points,
            arrivee = a.destination,
            guidage = false,
        )
        else -> null
    }

    // Forme « if / else » : dans un « when », « -> { … } » serait lu comme un bloc et non comme une fonction.
    val bandeauBas: (@Composable () -> Unit)? = if (categorie != null) {
        @Composable {
            CarteCategorie(
                vue = categorie,
                reference = position,
                onFiche = { choisir(categorie.choisi) },
                onItineraire = { calculerItineraire(categorie.choisi) },
                onIndex = ::mettreEnAvant,
            )
        }
    } else {
        null
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
            trajet = affichageTrajet,
            positionGuidage = (g as? EtatGuidage.EnCours)?.position,
            recentrage = recentrage,
            onSuivi = { suivi = it },
            bandeauBas = bandeauBas,
        )

        if (ecran == Ecran.CARTE) {
            when {
                g is EtatGuidage.EnCours -> GuidageEcran(
                    etat = g,
                    muet = muet,
                    suivi = suivi,
                    onRecentrer = { recentrage++ },
                    onMuet = guidage::couperVoix,
                    onArreter = { guidage.arreter() },
                )
                g is EtatGuidage.Arrive -> ArriveeCarte(
                    etat = g,
                    onTerminer = { guidage.arreter() },
                    onFavori = { choixListePour = g.destination },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                a != null -> ApercuTrajet(
                    etat = a,
                    onOptions = { calculerApercu(a.copy(options = it)) },
                    onChoisir = { apercu = a.copy(choisie = it) },
                    onDemarrer = { demarrer(simuler = false) },
                    onSimuler = { demarrer(simuler = true) },
                    onRetour = { apercu = null },
                )
            }
        }

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
