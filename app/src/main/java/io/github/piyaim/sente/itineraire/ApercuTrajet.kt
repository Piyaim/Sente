package io.github.piyaim.sente.itineraire

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.piyaim.sente.guidage.distanceRestante
import io.github.piyaim.sente.guidage.dureeLisible
import io.github.piyaim.sente.guidage.heureArrivee
import io.github.piyaim.sente.recherche.Lieu
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones

/** État de l'aperçu : destination, options, trajets proposés (null pendant le calcul) et celui choisi. */
data class EtatApercu(
    val destination: Lieu,
    val options: OptionsTrajet = OptionsTrajet(),
    val propositions: List<Proposition>? = null,
    val choisie: Int = 0,
    val erreur: String? = null,
) {
    val proposition: Proposition? get() = propositions?.getOrNull(choisie)
}

private val FOND_PEAGE = Color(0xFFF3E1A6)
private val ENCRE_PEAGE = Color(0xFF4A3A08)

/** Aperçu du trajet avant de démarrer (maquette « 6 · Aperçu du trajet »). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApercuTrajet(
    etat: EtatApercu,
    onOptions: (OptionsTrajet) -> Unit,
    onChoisir: (Int) -> Unit,
    onDemarrer: () -> Unit,
    onSimuler: () -> Unit,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoutonIcone(
                Icones.Retour, "Retour", onRetour,
                taille = 48.dp, rayon = 24.dp, fond = MaterialTheme.colorScheme.surface,
                encre = MaterialTheme.colorScheme.onSurface, ombre = false,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Depuis ma position", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Vers ${etat.destination.nom}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Option("Éviter les péages", etat.options.eviterPeages) { onOptions(etat.options.copy(eviterPeages = it)) }
                    Option("Éviter les autoroutes", etat.options.eviterAutoroutes) { onOptions(etat.options.copy(eviterAutoroutes = it)) }
                }

                val propositions = etat.propositions
                when {
                    etat.erreur != null -> Text(etat.erreur, style = MaterialTheme.typography.bodyLarge)
                    propositions == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                    else -> propositions.forEachIndexed { i, p ->
                        CarteProposition(p, principale = i == 0, choisie = i == etat.choisie) { onChoisir(i) }
                    }
                }

                Button(
                    onClick = onDemarrer,
                    enabled = etat.proposition != null,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                ) {
                    Icon(Icones.Lecture, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Démarrer", style = MaterialTheme.typography.titleLarge)
                }
                // Pour les préversions : essayer le guidage sans rouler.
                TextButton(onClick = onSimuler, enabled = etat.proposition != null, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Essai : simuler le trajet")
                }
            }
        }
    }
}

@Composable
private fun Option(texte: String, active: Boolean, onChange: (Boolean) -> Unit) {
    val forme = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(forme)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(2.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, forme)
            .clickable(role = Role.Switch) { onChange(!active) }
            .semantics { selected = active }
            .height(44.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteProposition(p: Proposition, principale: Boolean, choisie: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (choisie) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (choisie) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics { selected = choisie },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    dureeLisible(p.dureeSecondes),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (principale) 30.sp else 22.sp, lineHeight = 32.sp),
                )
                Text(
                    "${distanceRestante(p.distanceMetres)} · arrivée ${heureArrivee(p.dureeSecondes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (principale) Etiquette("Le plus rapide", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                if (p.sansPeage) Etiquette("Sans péage", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                if (p.peages) Etiquette("Péages", FOND_PEAGE, ENCRE_PEAGE)
                if (p.resume.isNotBlank()) {
                    Etiquette("Par ${p.resume}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Etiquette(texte: String, fond: Color, encre: Color) {
    Text(
        texte,
        modifier = Modifier.background(fond, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = encre,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
