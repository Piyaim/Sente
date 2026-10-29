package io.github.piyaim.sente.regions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.piyaim.sente.BuildConfig
import io.github.piyaim.sente.ui.dateLisible
import io.github.piyaim.sente.ui.tailleLisible

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionsEcran(gestion: GestionRegions, onRetour: () -> Unit) {
    val messages = remember { SnackbarHostState() }
    var aSupprimer by remember { mutableStateOf<RegionInstallee?>(null) }
    var aTelecharger by remember { mutableStateOf<RegionCatalogue?>(null) }
    val choixFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) gestion.importer(uri)
    }

    LaunchedEffect(gestion.message) {
        val texte = gestion.message ?: return@LaunchedEffect
        messages.showSnackbar(texte)
        gestion.message = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cartes hors ligne") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la carte")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(messages) },
    ) { marges ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Sur le téléphone") {
                if (gestion.installees.isEmpty()) {
                    Text("Aucune carte installée.")
                }
                gestion.installees.forEach { region ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = gestion.affichee?.id == region.id,
                            onClick = { gestion.afficher(region.id) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(region.nom, style = MaterialTheme.typography.titleMedium)
                            val details = listOfNotNull(
                                tailleLisible(region.taille),
                                dateLisible(region.donnees)?.let { "données du $it" },
                                if (region.origine == "import") "importée" else null,
                            )
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { aSupprimer = region }) { Text("Supprimer") }
                    }
                }
            }

            Section("Télécharger une région") {
                val etat = gestion.telechargement
                when {
                    etat is EtatTelechargement.EnCours -> {
                        Text(if (etat.enPause) "${etat.nom} : en attente du réseau…" else "${etat.nom} : téléchargement…")
                        LinearProgressIndicator(
                            progress = { if (etat.total > 0) etat.faits.toFloat() / etat.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${tailleLisible(etat.faits)} sur ${tailleLisible(etat.total)}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { gestion.annulerTelechargement() }) { Text("Annuler") }
                    }
                    etat is EtatTelechargement.Verification -> {
                        Text("${etat.nom} : vérification du fichier…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        val catalogue = gestion.catalogue
                        if (catalogue == null) {
                            Text(
                                "La liste des régions est lue sur github.com (dépôt Piyaim/Sente-cartes), uniquement quand vous appuyez sur le bouton.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onClick = { gestion.chargerCatalogue() }, enabled = !gestion.chargementCatalogue) {
                                if (gestion.chargementCatalogue) {
                                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                                }
                                Text("Voir les régions disponibles")
                            }
                        } else if (catalogue.isEmpty()) {
                            Text("Aucune région publiée pour l'instant.")
                        } else {
                            catalogue.forEach { region ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(region.nom, style = MaterialTheme.typography.titleMedium)
                                        val details = listOfNotNull(
                                            tailleLisible(region.taille),
                                            dateLisible(region.donnees)?.let { "données du $it" },
                                        )
                                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { aTelecharger = region }) { Text("Télécharger") }
                                }
                            }
                        }
                    }
                }
            }

            Section("Importer un fichier") {
                Text(
                    "Pour n'avoir aucun échange réseau : copiez un fichier .pmtiles sur le téléphone, puis importez-le.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { choixFichier.launch(arrayOf("*/*")) }, enabled = !gestion.importEnCours) {
                    Text(if (gestion.importEnCours) "Import en cours…" else "Choisir un fichier")
                }
                if (gestion.importEnCours) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Section("Position") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Première position plus rapide", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Utilise aussi le fournisseur « réseau » d'Android. Sur un téléphone avec les services Google, " +
                                "le système envoie alors à Google les réseaux Wi-Fi et antennes autour de vous. Désactivé : GPS seul.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = gestion.fournisseurReseau, onCheckedChange = { gestion.definirFournisseurReseau(it) })
                }
            }

            Section("À propos") {
                Text("Sente ${BuildConfig.VERSION_NAME} · licence GNU AGPL v3 ou ultérieure", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Carte : © les contributeurs d'OpenStreetMap (ODbL), tuiles Protomaps. Aucune donnée collectée.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    aSupprimer?.let { region ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer ${region.nom} ?") },
            text = { Text("La carte (${tailleLisible(region.taille)}) sera effacée du téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    gestion.supprimer(region.id)
                    aSupprimer = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } },
        )
    }

    aTelecharger?.let { region ->
        AlertDialog(
            onDismissRequest = { aTelecharger = null },
            title = { Text("Télécharger ${region.nom} ?") },
            text = {
                Text(
                    "${tailleLisible(region.taille)} depuis github.com. Le Wi-Fi est conseillé. " +
                        "GitHub voit votre adresse IP, comme pour tout téléchargement ; Sente n'envoie aucun identifiant.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    gestion.telecharger(region)
                    aTelecharger = null
                }) { Text("Télécharger") }
            },
            dismissButton = { TextButton(onClick = { aTelecharger = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titre, style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            contenu()
        }
    }
}
