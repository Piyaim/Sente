package io.github.piyaim.sente.regions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.piyaim.sente.BuildConfig
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Groupe
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.Separateur
import io.github.piyaim.sente.ui.TitreSection
import io.github.piyaim.sente.ui.dateLisible
import io.github.piyaim.sente.ui.tailleLisible
import java.time.LocalDate

@Composable
fun RegionsEcran(
    gestion: GestionRegions,
    onExporter: (Uri) -> Unit,
    onToutEffacer: () -> Unit,
    onRetour: () -> Unit,
) {
    val messages = remember { SnackbarHostState() }
    var aSupprimer by remember { mutableStateOf<RegionInstallee?>(null) }
    var aTelecharger by remember { mutableStateOf<Pair<RegionCatalogue, List<Tache>>?>(null) }
    var effacement by remember { mutableStateOf(false) }
    val choixFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) gestion.importer(uri)
    }
    val choixExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExporter(uri)
    }

    LaunchedEffect(gestion.message) {
        val texte = gestion.message ?: return@LaunchedEffect
        messages.showSnackbar(texte)
        gestion.message = null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoutonIcone(
                    Icones.Retour, "Retour à la carte", onRetour,
                    taille = 48.dp, rayon = 24.dp, fond = MaterialTheme.colorScheme.background,
                    encre = MaterialTheme.colorScheme.onBackground, ombre = false,
                )
                Text("Cartes et réglages", style = MaterialTheme.typography.headlineSmall)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Section("Sur le téléphone") {
                    if (gestion.installees.isEmpty()) Box(Modifier.padding(16.dp)) { Paragraphe("Aucune carte installée.") }
                    gestion.installees.forEachIndexed { i, region ->
                        if (i > 0) Separateur()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = gestion.affichee?.id == region.id, role = Role.RadioButton) { gestion.afficher(region.id) }
                                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = gestion.affichee?.id == region.id, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(region.nom, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(
                                        "Carte ${tailleLisible(region.taille)}",
                                        dateLisible(region.donnees)?.let { "du $it" },
                                        if (region.origine == "import") "importée" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (region.recherche != null) {
                                        listOfNotNull("Recherche ${tailleLisible(region.tailleRecherche)}", dateLisible(region.rechercheDonnees)?.let { "du $it" }).joinToString(" · ")
                                    } else {
                                        "Recherche non installée"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { aSupprimer = region }) { Text("Supprimer") }
                        }
                    }
                }

                Section("Télécharger") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (val etat = gestion.telechargement) {
                            is EtatTelechargement.EnCours -> {
                                val quoi = etat.tache.type.libelle
                                Text(
                                    "${etat.tache.regionNom} · $quoi (${etat.etape}/${etat.etapes}) : " +
                                        if (etat.enPause) "en attente du réseau…" else "téléchargement…",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(
                                    progress = { if (etat.total > 0) etat.faits.toFloat() / etat.total else 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("${tailleLisible(etat.faits)} sur ${tailleLisible(etat.total)}", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { gestion.annulerTelechargement() }) { Text("Annuler") }
                            }
                            is EtatTelechargement.Verification -> {
                                Text("${etat.tache.regionNom} · ${etat.tache.type.libelle} : vérification du fichier…", style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            null -> {
                                val catalogue = gestion.catalogue
                                when {
                                    catalogue == null -> {
                                        Paragraphe("La liste des régions est lue sur github.com (dépôt Piyaim/Sente-cartes), uniquement quand vous appuyez sur le bouton.")
                                        Button(onClick = { gestion.chargerCatalogue() }, enabled = !gestion.chargementCatalogue) {
                                            if (gestion.chargementCatalogue) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text("Voir les régions disponibles")
                                        }
                                    }
                                    catalogue.isEmpty() -> Paragraphe("Aucune région publiée pour l'instant.")
                                    else -> catalogue.forEach { region ->
                                        val taches = gestion.aTelecharger(region)
                                        val installee = gestion.installees.any { it.id == region.id }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(region.nom, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    listOfNotNull(
                                                        "Carte ${tailleLisible(region.carte.taille)}",
                                                        region.recherche?.let { "recherche ${tailleLisible(it.taille)}" },
                                                    ).joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            when {
                                                taches.isEmpty() -> Text("À jour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                                else -> TextButton(onClick = { aTelecharger = region to taches }) {
                                                    Text(
                                                        when {
                                                            !installee -> "Télécharger"
                                                            taches.all { it.type == TypeFichier.RECHERCHE } -> "Ajouter la recherche"
                                                            else -> "Mettre à jour"
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Section("Importer un fichier") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Paragraphe("Pour n'avoir aucun échange réseau : copiez sur le téléphone la carte (.pmtiles) et la recherche (.recherche.db) d'une région, puis importez-les.")
                        OutlinedButton(onClick = { choixFichier.launch(arrayOf("*/*")) }, enabled = !gestion.importEnCours) {
                            Text(if (gestion.importEnCours) "Import en cours…" else "Choisir un fichier")
                        }
                        if (gestion.importEnCours) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                Section("Position") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Première position plus rapide", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Utilise aussi le fournisseur « réseau » d'Android. Sur un téléphone avec les services Google, " +
                                    "le système envoie alors à Google les réseaux Wi-Fi et antennes autour de vous. Désactivé : GPS seul.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = gestion.fournisseurReseau, onCheckedChange = { gestion.definirFournisseurReseau(it) })
                    }
                }

                Section("Mes données") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Paragraphe("Favoris, listes, domicile, travail et recherches récentes restent sur ce téléphone.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { choixExport.launch("sente-donnees-${LocalDate.now()}.json") }) { Text("Exporter") }
                            TextButton(onClick = { effacement = true }) { Text("Tout effacer") }
                        }
                    }
                }

                Section("À propos") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sente ${BuildConfig.VERSION_NAME} · licence GNU AGPL v3 ou ultérieure", style = MaterialTheme.typography.bodyMedium)
                        Paragraphe(
                            "Carte et lieux : © les contributeurs d'OpenStreetMap (ODbL), tuiles Protomaps. " +
                                "Adresses : Base Adresse Nationale (Licence Ouverte 2.0). Aucune donnée collectée.",
                        )
                    }
                }
            }
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }

    aSupprimer?.let { region ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer ${region.nom} ?") },
            text = { Text("La carte et la recherche (${tailleLisible(region.taille + region.tailleRecherche)}) seront effacées du téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    gestion.supprimer(region.id)
                    aSupprimer = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } },
        )
    }

    aTelecharger?.let { (region, taches) ->
        val total = taches.sumOf { it.fichier.taille }
        AlertDialog(
            onDismissRequest = { aTelecharger = null },
            title = { Text("Télécharger ${region.nom} ?") },
            text = {
                Text(
                    taches.joinToString(" et ") { it.type.libelle }.replaceFirstChar { it.uppercase() } +
                        " : ${tailleLisible(total)} depuis github.com. Le Wi-Fi est conseillé. " +
                        "GitHub voit votre adresse IP, comme pour tout téléchargement ; Sente n'envoie aucun identifiant.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    gestion.telecharger(taches)
                    aTelecharger = null
                }) { Text("Télécharger") }
            },
            dismissButton = { TextButton(onClick = { aTelecharger = null }) { Text("Annuler") } },
        )
    }

    if (effacement) {
        AlertDialog(
            onDismissRequest = { effacement = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Favoris, listes, domicile, travail et recherches récentes seront supprimés de ce téléphone. Les cartes restent installées.") },
            confirmButton = {
                TextButton(onClick = {
                    onToutEffacer()
                    effacement = false
                }) { Text("Tout effacer") }
            },
            dismissButton = { TextButton(onClick = { effacement = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TitreSection(titre)
        Groupe(contenu)
    }
}

@Composable
private fun Paragraphe(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
