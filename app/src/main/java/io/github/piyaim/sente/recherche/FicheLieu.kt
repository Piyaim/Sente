package io.github.piyaim.sente.recherche

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.piyaim.sente.perso.Raccourci
import io.github.piyaim.sente.ui.ActionFiche
import io.github.piyaim.sente.ui.Icones
import java.time.LocalDate
import java.util.Locale

/** Fiche d'un lieu, posée en bas de la carte (maquette « 3 · Fiche de lieu »). */
@Composable
fun FicheLieu(
    lieu: Lieu,
    reference: Reference?,
    favori: Boolean,
    onFermer: () -> Unit,
    onItineraire: () -> Unit,
    onFavori: () -> Unit,
    onRaccourci: (Raccourci) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val horaires = remember(lieu.horaires) { Horaires.lire(lieu.horaires) }
    val etat = horaires?.etat()
    var semaineOuverte by remember(lieu) { mutableStateOf(false) }
    val hauteurMax = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = hauteurMax),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .padding(start = 48.dp)
                            .width(40.dp)
                            .height(5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
                    )
                }
                TextButton(onClick = onFermer) { Text("Fermer") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lieu.nom, style = MaterialTheme.typography.headlineSmall)
                val distance = reference?.let { distanceLisible(distanceMetres(it.lat, it.lon, lieu.lat, lieu.lon)) }
                Text(
                    listOfNotNull(lieu.sousTitre.ifBlank { null }, distance).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (etat != null) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append(if (etat.ouvert) "Ouvert" else "Fermé")
                            }
                            etat.detail?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Button(
                onClick = onItineraire,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icones.Itineraire, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Itinéraire", style = MaterialTheme.typography.titleLarge)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionFiche(Icones.Etoile, if (favori) "Enregistré" else "Favori", actif = favori, onClick = onFavori)
                lieu.telephone?.let { tel ->
                    ActionFiche(Icones.Telephone, "Appeler", onClick = { ouvrir(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tel.filter { it.isDigit() || it == '+' }))) })
                }
                lieu.site?.let { site ->
                    ActionFiche(Icones.Globe, "Site web", onClick = { ouvrir(context, Intent(Intent.ACTION_VIEW, Uri.parse(adresseWeb(site)))) })
                }
                ActionFiche(Icones.Partager, "Partager", onClick = { partager(context, lieu) })
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (lieu.adresse ?: if (lieu.type != TypeLieu.LIEU) null else "Adresse non renseignée")?.let {
                    Detail(Icones.Repere) { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                if (horaires != null) {
                    val aujourdhui = LocalDate.now().dayOfWeek.value - 1
                    val lignes = horaires.lignes()
                    Detail(Icones.Horloge) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Aujourd'hui ") }
                                    append(lignes[aujourdhui].second)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (semaineOuverte) {
                                lignes.forEachIndexed { i, (jour, plages) ->
                                    Text(
                                        "$jour  $plages",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (i == aujourdhui) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                            Text(
                                if (semaineOuverte) "Masquer la semaine" else "Toute la semaine",
                                modifier = Modifier.clickable(role = Role.Button) { semaineOuverte = !semaineOuverte }.padding(vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (lieu.horaires != null) {
                    Detail(Icones.Horloge) { Text(lieu.horaires, style = MaterialTheme.typography.bodyLarge) }
                }
                lieu.telephone?.let { Detail(Icones.Telephone) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                accesFauteuil(lieu.fauteuil)?.let { Detail(Icones.Fauteuil) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enregistrer comme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onRaccourci(Raccourci.DOMICILE) }) { Text("Domicile") }
                    TextButton(onClick = { onRaccourci(Raccourci.TRAVAIL) }) { Text("Travail") }
                }
            }

            val source = when (lieu.type) {
                TypeLieu.ADRESSE, TypeLieu.RUE, TypeLieu.COMMUNE -> "Base Adresse Nationale"
                else -> "OpenStreetMap"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Données $source", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lieu.osm?.let { osm ->
                    TextButton(onClick = { ouvrir(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/$osm"))) }) {
                        Text("Corriger une erreur")
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(icone: ImageVector, contenu: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        contenu()
    }
}

private fun accesFauteuil(valeur: String?): String? = when (valeur) {
    "yes" -> "Accès en fauteuil roulant"
    "limited" -> "Accès en fauteuil roulant limité"
    "no" -> "Pas d'accès en fauteuil roulant"
    else -> null
}

private fun adresseWeb(site: String): String = if (site.startsWith("http://") || site.startsWith("https://")) site else "https://$site"

private fun ouvrir(context: Context, intention: Intent) {
    try {
        context.startActivity(intention)
    } catch (e: ActivityNotFoundException) {
        // Aucune appli pour ouvrir ce lien : rien à faire.
    }
}

private fun partager(context: Context, lieu: Lieu) {
    val lat = "%.6f".format(Locale.ROOT, lieu.lat)
    val lon = "%.6f".format(Locale.ROOT, lieu.lon)
    val texte = listOfNotNull(
        lieu.nom,
        lieu.adresse ?: lieu.sousTitre.ifBlank { null },
        "geo:$lat,$lon",
        "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon",
    ).joinToString("\n")
    val envoi = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texte)
    ouvrir(context, Intent.createChooser(envoi, "Partager ${lieu.nom}"))
}
