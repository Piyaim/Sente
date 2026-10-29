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
import io.github.piyaim.sente.perso.ChoixListe
import io.github.piyaim.sente.perso.DonneesPerso
import io.github.piyaim.sente.perso.FavorisEcran
import io.github.piyaim.sente.perso.Raccourci
import io.github.piyaim.sente.recherche.FicheLieu
import io.github.piyaim.sente.recherche.GestionRecherche
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.RechercheEcran
import io.github.piyaim.sente.recherche.Reference
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
    val messages = remember { SnackbarHostState() }

    var ecran by rememberSaveable { mutableStateOf(Ecran.CARTE) }
    var selectionJson by rememberSaveable { mutableStateOf<String?>(null) }
    val selection = remember(selectionJson) { selectionJson?.let { Lieu.depuisJson(JSONObject(it)) } }
    var positionAutorisee by remember { mutableStateOf(positionPreciseAccordee(context)) }
    var position by remember { mutableStateOf<Reference?>(null) }
    var centre by remember { mutableStateOf<Reference?>(null) }
    var versionPerso by remember { mutableStateOf(0) } // relit favoris et historique après une modification
    var choixListePour by remember { mutableStateOf<Lieu?>(null) }

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

    BackHandler(enabled = ecran != Ecran.CARTE || selection != null) {
        if (ecran != Ecran.CARTE) ecran = Ecran.CARTE else selectionJson = null
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
        )

        val lieu = selection
        if (lieu != null && ecran == Ecran.CARTE) {
            FicheLieu(
                lieu = lieu,
                reference = position,
                favori = favori,
                onFermer = { selectionJson = null },
                onItineraire = { afficherMessage("Le calcul d'itinéraire arrive dans la prochaine version.") },
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
