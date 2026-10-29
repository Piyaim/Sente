package io.github.piyaim.sente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pastille blanche posée sur la carte (Domicile, Travail, Favoris). */
@Composable
fun PastilleCarte(icone: ImageVector, texte: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(texte, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Puce de catégorie (recherche). */
@Composable
fun PuceCategorie(texte: String, choisie: Boolean, onClick: () -> Unit) {
    val fond = if (choisie) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val encre = if (choisie) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fond)
            .clickable(role = Role.Button, onClick = onClick)
            .height(44.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, style = MaterialTheme.typography.labelLarge, color = encre)
    }
}

/** Bouton carré arrondi à icône seule (calques, retour…). */
@Composable
fun BoutonIcone(
    icone: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    taille: Dp = 56.dp,
    rayon: Dp = 18.dp,
    fond: Color = MaterialTheme.colorScheme.surface,
    encre: Color = MaterialTheme.colorScheme.primary,
    ombre: Boolean = true,
) {
    Box(
        modifier = modifier
            .then(if (ombre) Modifier.shadow(2.dp, RoundedCornerShape(rayon)) else Modifier)
            .clip(RoundedCornerShape(rayon))
            .background(fond)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .size(taille),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = description, tint = encre, modifier = Modifier.size(24.dp))
    }
}

/** Action de fiche : carré tonal + libellé dessous. */
@Composable
fun ActionFiche(icone: ImageVector, texte: String, actif: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (actif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = if (actif) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(texte, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Titre de section en petites capitales, comme dans les maquettes. */
@Composable
fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelMedium.fontSize * 0.06),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Bloc blanc arrondi qui regroupe des lignes. */
@Composable
fun Groupe(contenu: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column { contenu() }
    }
}

/** Ligne de résultat : vignette, titre, sous-titre, texte à droite. */
@Composable
fun LigneResultat(
    icone: ImageVector,
    titre: @Composable () -> Unit,
    sousTitre: String,
    droite: String?,
    onClick: () -> Unit,
    vignettePleine: Boolean = false,
    fin: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (vignettePleine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = if (vignettePleine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            titre()
            if (sousTitre.isNotEmpty()) {
                Text(
                    sousTitre,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (droite != null) {
            Text(droite, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        fin?.invoke()
    }
}

@Composable
fun Separateur() {
    Box(
        Modifier
            .padding(start = 74.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
