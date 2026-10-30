package io.github.piyaim.sente.perso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.recherche.distanceLisible
import io.github.piyaim.sente.recherche.distanceMetres
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Groupe
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.LigneResultat
import io.github.piyaim.sente.ui.Separateur
import io.github.piyaim.sente.ui.TitreSection

@Composable
fun FavorisEcran(
    listes: List<ListeFavoris>,
    raccourcis: Map<Raccourci, Lieu?>,
    reference: Reference?,
    onChoisir: (Lieu) -> Unit,
    onRetirer: (Lieu, String) -> Unit,
    onRenommer: (Lieu, String) -> Unit,
    onOublierRaccourci: (Raccourci) -> Unit,
    onRetour: () -> Unit,
) {
    var aRenommer by remember { mutableStateOf<Lieu?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
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
            Text("Favoris", style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TitreSection("Raccourcis")
                Groupe {
                    Raccourci.entries.forEachIndexed { i, r ->
                        if (i > 0) Separateur()
                        val lieu = raccourcis[r]
                        val oubli: (@Composable () -> Unit)? = if (lieu == null) {
                            null
                        } else {
                            @Composable { BoutonRetirer("Oublier ${r.libelle.lowercase()}") { onOublierRaccourci(r) } }
                        }
                        LigneResultat(
                            icone = if (r == Raccourci.DOMICILE) Icones.Domicile else Icones.Travail,
                            vignettePleine = lieu != null,
                            titre = { Text(r.libelle, style = MaterialTheme.typography.titleMedium) },
                            sousTitre = lieu?.let { it.adresse ?: it.nom } ?: "Non défini : ouvrez un lieu puis « Enregistrer comme »",
                            droite = null,
                            onClick = { lieu?.let(onChoisir) },
                            fin = oubli,
                        )
                    }
                }
            }

            if (listes.isEmpty()) {
                Text(
                    "Aucun favori. Ouvrez un lieu et touchez « Favori » pour l'enregistrer ici. Tout reste sur le téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            listes.forEach { liste ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TitreSection("${liste.nom} · ${liste.lieux.size}")
                    Groupe {
                        liste.lieux.forEachIndexed { i, lieu ->
                            if (i > 0) Separateur()
                            LigneResultat(
                                icone = Icones.Etoile,
                                titre = { Text(lieu.nom, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                sousTitre = lieu.sousTitre,
                                droite = reference?.let { distanceLisible(distanceMetres(it.lat, it.lon, lieu.lat, lieu.lon)) },
                                onClick = { onChoisir(lieu) },
                                fin = {
                                    Row {
                                        BoutonIcone(
                                            Icones.Crayon, "Renommer ${lieu.nom}", { aRenommer = lieu },
                                            taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.surface,
                                            encre = MaterialTheme.colorScheme.onSurfaceVariant, ombre = false,
                                        )
                                        BoutonRetirer("Retirer ${lieu.nom}") { onRetirer(lieu, liste.nom) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    aRenommer?.let { lieu ->
        DialogueRenommer(
            lieu,
            onValider = { nom ->
                onRenommer(lieu, nom)
                aRenommer = null
            },
            onAnnuler = { aRenommer = null },
        )
    }
}

@Composable
private fun DialogueRenommer(lieu: Lieu, onValider: (String) -> Unit, onAnnuler: () -> Unit) {
    var nom by remember { mutableStateOf(lieu.nom) }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Renommer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it.take(60) },
                    singleLine = true,
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth(),
                )
                lieu.nomOrigine?.let {
                    Text("Nom d'origine : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onValider(nom) }) { Text("Renommer") } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}

@Composable
private fun BoutonRetirer(description: String, onClick: () -> Unit) {
    BoutonIcone(
        Icones.Supprimer, description, onClick,
        taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.surface,
        encre = MaterialTheme.colorScheme.onSurfaceVariant, ombre = false,
    )
}

/** Choix de la liste où enregistrer un favori (ou création d'une nouvelle liste). */
@Composable
fun ChoixListe(
    nomInitial: String,
    nomsExistants: List<String>,
    onChoisir: (liste: String, nom: String) -> Unit,
    onAnnuler: () -> Unit,
) {
    var nomLieu by remember { mutableStateOf(nomInitial) }
    var choisie by remember { mutableStateOf(nomsExistants.firstOrNull() ?: LISTE_PAR_DEFAUT) }
    var nouvelle by remember { mutableStateOf("") }
    var creation by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Enregistrer dans") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = nomLieu,
                    onValueChange = { nomLieu = it.take(60) },
                    singleLine = true,
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                nomsExistants.forEach { nom ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = !creation && choisie == nom, role = Role.RadioButton) {
                                choisie = nom
                                creation = false
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !creation && choisie == nom, onClick = null)
                        Text(nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = creation, role = Role.RadioButton) { creation = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = creation, onClick = null)
                    Text("Nouvelle liste…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
                if (creation) {
                    OutlinedTextField(
                        value = nouvelle,
                        onValueChange = { nouvelle = it.take(40) },
                        singleLine = true,
                        label = { Text("Nom de la liste") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChoisir(if (creation) nouvelle.trim() else choisie, nomLieu) },
                enabled = !creation || nouvelle.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}
