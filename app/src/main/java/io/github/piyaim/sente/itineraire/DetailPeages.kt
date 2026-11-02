package io.github.piyaim.sente.itineraire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.piyaim.sente.guidage.distanceRestante
import io.github.piyaim.sente.guidage.dureeLisible
import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones
import io.github.piyaim.sente.ui.dateLisible
import java.util.Locale
import kotlin.math.roundToInt

private val FOND_GARE = Color(0xFFF3E1A6)
private val ENCRE_GARE = Color(0xFF4A3A08)

/** Détail des péages du trajet choisi (maquette « 10 · Péages du trajet »). */
@Composable
fun DetailPeages(
    proposition: Proposition,
    cout: CoutPeages?,
    tarifs: TarifsPeages?,
    classe: ClassePeage,
    budget: Int?,
    onClasse: (ClassePeage) -> Unit,
    onBudget: (Int?) -> Unit,
    onEviter: (Reference) -> Unit,
    onRetour: () -> Unit,
) {
    val hauteurMax = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    Column(
        Modifier.fillMaxWidth().heightIn(max = hauteurMax).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BoutonIcone(
                Icones.Retour, "Retour aux trajets", onRetour,
                taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.surface,
                encre = MaterialTheme.colorScheme.onSurface, ombre = false,
            )
            Text(
                "Péages du trajet",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 26.sp),
            )
            ChoixClasse(classe, onClasse)
        }

        if (cout == null) {
            Text(
                "Prix inconnus : ajoutez les péages de la région dans « Cartes et réglages ».",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        cout.totalCentimes == 0 && !cout.complet -> "Prix inconnu"
                        cout.complet -> prixLisible(cout.totalCentimes)
                        else -> "≥ " + prixLisible(cout.totalCentimes)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 34.sp, lineHeight = 36.sp),
                )
                val n = cout.sections.size
                Text(
                    "${dureeLisible(proposition.dureeSecondes)} · ${distanceRestante(proposition.distanceMetres)} · " +
                        if (n == 0) "sans péage" else if (n == 1) "1 section payante" else "$n sections payantes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (cout.sections.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                ) {
                    cout.sections.forEachIndexed { i, section ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 68.dp).height1().background(MaterialTheme.colorScheme.outlineVariant))
                        LigneSection(section) { onEviter(section.evitement) }
                    }
                }
            }
            if (cout.estime) {
                Text(
                    "« Estimé » : prix déduit de la sortie connue la plus proche, ramené à la distance (écart habituel de quelques %).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!cout.complet) {
                Text(
                    "Certains péages ne sont pas dans la base de tarifs : le total est peut-être incomplet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Budget(budget, onBudget)
        if (budget != null && cout != null && cout.totalCentimes > budget) {
            Text(
                "Ce trajet dépasse votre budget de ${prixLisible(cout.totalCentimes - budget)}. " +
                    "Évitez une section ci-dessus, ou revenez aux trajets pour la variante sans péage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            "Prix indicatifs" + (tarifs?.dateTarifs?.let { dateLisible(it) }?.let { ", tarifs du $it" } ?: "") +
                " · données OpenTollData (ODbL)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LigneSection(section: SectionPeage, onEviter: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FOND_GARE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icones.Peage, contentDescription = null, tint = ENCRE_GARE, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val trajet = when {
                    section.horsBase -> "${section.entree} · péage non répertorié"
                    section.ouverte -> "Barrière de ${section.entree}"
                    section.sortie != null -> "${section.entree} → ${section.sortie}"
                    else -> "${section.entree} → sortie inconnue"
                }
                Text(
                    listOfNotNull(section.route, trajet).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    listOfNotNull(
                        section.operateur.ifBlank { null },
                        section.metres?.let { "${(it / 1000).roundToInt()} km" },
                        if (section.estime) "estimé" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                section.centimes?.let { (if (section.estime) "≈ " else "") + prixLisible(it) } ?: "prix inconnu",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
            )
        }
        OutlinedButton(onClick = onEviter, modifier = Modifier.padding(start = 52.dp)) {
            Text("Éviter cette section")
        }
    }
}

@Composable
private fun ChoixClasse(classe: ClassePeage, onClasse: (ClassePeage) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(3.dp),
    ) {
        ClassePeage.entries.forEach { c ->
            val choisie = c == classe
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (choisie) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onClasse(c) }
                    .semantics { selected = choisie }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.libelle,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (choisie) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Budget(budget: Int?, onBudget: (Int?) -> Unit) {
    var texte by remember { mutableStateOf(budget?.let { "%.2f".format(Locale.FRANCE, it / 100.0).removeSuffix(",00") } ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Budget péage maximum", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = texte,
            onValueChange = { saisie ->
                val propre = saisie.filter { it.isDigit() || it == ',' || it == '.' }.take(7)
                texte = propre
                // Vide : pas de budget. Sinon des euros, avec virgule ou point.
                onBudget(propre.replace(',', '.').toDoubleOrNull()?.let { (it * 100).roundToInt() })
            },
            singleLine = true,
            suffix = { Text("€") },
            placeholder = { Text("aucun") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(120.dp),
        )
    }
}

/** Ligne de séparation d'un pixel logique. */
private fun Modifier.height1(): Modifier = this.then(Modifier.heightIn(min = 1.dp, max = 1.dp))
