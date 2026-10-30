package io.github.piyaim.sente.recherche

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.piyaim.sente.regions.RegionInstallee
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Groupe
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.LigneResultat
import io.github.piyaim.sente.ui.PuceCategorie
import io.github.piyaim.sente.ui.Separateur
import io.github.piyaim.sente.ui.TitreSection
import kotlinx.coroutines.delay

@Composable
fun RechercheEcran(
    region: RegionInstallee?,
    recherche: GestionRecherche,
    reference: Reference?,
    historique: List<Lieu>,
    categorie: VueCategorie?,
    onCategorie: (code: String?, libelle: String) -> Unit,
    onChoisir: (Lieu) -> Unit,
    onEffacerHistorique: () -> Unit,
    onRetour: () -> Unit,
    onOuvrirRegions: () -> Unit,
) {
    var texte by rememberSaveable { mutableStateOf("") }
    var resultats by remember { mutableStateOf<Resultats?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val clavier = LocalSoftwareKeyboardController.current
    val ref = reference ?: region?.let { Reference(it.enTete.latCentre, it.enTete.lonCentre) }
    val indexDisponible = region?.recherche != null

    // Retour depuis la carte d'une catégorie : on montre sa liste, sans ouvrir le clavier.
    LaunchedEffect(Unit) { if (categorie == null) focus.requestFocus() }

    // Recherche au fil de la frappe, après une courte pause.
    LaunchedEffect(texte, region?.recherche?.absolutePath) {
        if (texte.isBlank() || ref == null) {
            resultats = null
            return@LaunchedEffect
        }
        delay(250)
        enCours = true
        try {
            resultats = recherche.chercher(region, texte, ref)
        } finally {
            enCours = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BoutonIcone(
                Icones.Retour, "Retour à la carte", onRetour,
                taille = 48.dp, rayon = 24.dp, fond = MaterialTheme.colorScheme.background,
                encre = MaterialTheme.colorScheme.onBackground, ombre = false,
            )
            ChampRecherche(
                texte = texte,
                onTexte = {
                    texte = it
                    if (it.isNotBlank() && categorie != null) onCategorie(null, "")
                },
                onValider = { clavier?.hide() },
                focus = focus,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CATEGORIES_RAPIDES.forEach { (code, libelle) ->
                // Une catégorie s'affiche directement sur la carte (maquette « 4 · Catégorie sur la carte »).
                PuceCategorie(libelle, choisie = categorie?.code == code) {
                    if (categorie?.code == code) {
                        onCategorie(null, libelle)
                    } else {
                        texte = ""
                        clavier?.hide()
                        onCategorie(code, libelle)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                region == null -> AvisIndex("Aucune carte installée", "Installez d'abord une région.", onOuvrirRegions)
                !indexDisponible -> AvisIndex(
                    "Recherche non installée",
                    "Téléchargez la recherche de ${region.nom} pour trouver adresses et lieux sans réseau.",
                    onOuvrirRegions,
                )
                categorie != null && texte.isBlank() -> {
                    Section("${categorie.libelle} à proximité") { Lignes(categorie.lieux, ref, texte = "", onChoisir) }
                }
                texte.isNotBlank() -> {
                    val r = resultats
                    if (r == null) {
                        Chargement()
                    } else if (r.vide && !enCours) {
                        Vide("Aucun résultat pour « ${texte.trim()} ».")
                    } else {
                        if (r.adresses.isNotEmpty()) Section("Adresses") { Lignes(r.adresses, ref, texte, onChoisir) }
                        if (r.lieux.isNotEmpty()) Section("Lieux") { Lignes(r.lieux, ref, texte, onChoisir) }
                    }
                }
                historique.isNotEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TitreSection("Récents", Modifier.weight(1f))
                            TextButton(onClick = onEffacerHistorique) { Text("Tout effacer") }
                        }
                        Groupe {
                            historique.forEachIndexed { i, lieu ->
                                if (i > 0) Separateur()
                                LigneResultat(
                                    icone = Icones.Horloge,
                                    titre = { Text(lieu.nom, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    sousTitre = lieu.sousTitre,
                                    droite = null,
                                    onClick = { onChoisir(lieu) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (region != null && indexDisponible) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icones.Valide, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Recherche sur le téléphone · ${region.nom}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChampRecherche(
    texte: String,
    onTexte: (String) -> Unit,
    onValider: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (texte.isEmpty()) {
                Text("Adresse, lieu, commune…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = texte,
                onValueChange = onTexte,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onValider() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Rechercher" },
            )
        }
        if (texte.isNotEmpty()) {
            BoutonIcone(
                Icones.Effacer, "Effacer", { onTexte("") },
                taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.secondaryContainer,
                encre = MaterialTheme.colorScheme.onSurface, ombre = false,
            )
        }
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
private fun Lignes(lieux: List<Lieu>, ref: Reference?, texte: String, onChoisir: (Lieu) -> Unit) {
    val normalise = Normalisation.normaliser(texte)
    val jetons = (Normalisation.jetons(normalise) + Normalisation.mots(normalise)).distinct()
    lieux.forEachIndexed { i, lieu ->
        if (i > 0) Separateur()
        val etat = if (lieu.type == TypeLieu.LIEU) Horaires.lire(lieu.horaires)?.etat() else null
        val sousTitre = listOfNotNull(
            lieu.sousTitre.ifBlank { null },
            etat?.let { if (it.ouvert) "Ouvert" else "Fermé" },
        ).joinToString(" · ")
        LigneResultat(
            icone = when (lieu.type) {
                TypeLieu.RUE -> Icones.Rue
                else -> Icones.Repere
            },
            vignettePleine = lieu.type == TypeLieu.ADRESSE,
            titre = {
                Text(
                    surligner(lieu.nom, jetons),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            sousTitre = sousTitre,
            droite = ref?.let { distanceLisible(distanceMetres(it.lat, it.lon, lieu.lat, lieu.lon)) },
            onClick = { onChoisir(lieu) },
        )
    }
}

/** Met en gras le début des mots qui correspondent à ce qui a été tapé. */
private fun surligner(nom: String, jetons: List<String>): AnnotatedString {
    if (jetons.isEmpty()) return AnnotatedString(nom)
    return buildAnnotatedString {
        Regex("\\S+|\\s+").findAll(nom).forEach { morceau ->
            val mot = morceau.value
            val normalise = Normalisation.normaliser(mot)
            val jeton = jetons.filter { normalise.startsWith(it) }.maxByOrNull { it.length }
            if (jeton != null && normalise.length == mot.filter { it.isLetterOrDigit() }.length) {
                // Compte les caractères du mot d'origine jusqu'à couvrir le jeton.
                var vus = 0
                var fin = 0
                while (fin < mot.length && vus < jeton.length) {
                    if (mot[fin].isLetterOrDigit()) vus++
                    fin++
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(mot.substring(0, fin))
                pop()
                append(mot.substring(fin))
            } else {
                append(mot)
            }
        }
    }
}

@Composable
private fun AvisIndex(titre: String, texte: String, onOuvrirRegions: () -> Unit) {
    Groupe {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titre, style = MaterialTheme.typography.titleLarge)
            Text(texte, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOuvrirRegions) { Text("Cartes hors ligne") }
        }
    }
}

@Composable
private fun Chargement() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun Vide(texte: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
    }
}
