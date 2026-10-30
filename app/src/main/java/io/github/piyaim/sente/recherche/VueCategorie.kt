package io.github.piyaim.sente.recherche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones
import org.json.JSONArray
import org.json.JSONObject

/** Résultats d'une catégorie (« Boulangerie ») affichés sur la carte, avec le lieu mis en avant. */
data class VueCategorie(val code: String, val libelle: String, val lieux: List<Lieu>, val index: Int = 0) {
    val choisi: Lieu get() = lieux[index]

    fun versJson(): String = JSONObject().apply {
        put("code", code)
        put("libelle", libelle)
        put("index", index)
        put("lieux", JSONArray().apply { lieux.forEach { put(it.versJson()) } })
    }.toString()

    companion object {
        fun depuisJson(texte: String): VueCategorie? = runCatching {
            val j = JSONObject(texte)
            val tableau = j.getJSONArray("lieux")
            val lieux = (0 until tableau.length()).mapNotNull { Lieu.depuisJson(tableau.getJSONObject(it)) }
            VueCategorie(j.getString("code"), j.getString("libelle"), lieux, j.getInt("index").coerceIn(0, lieux.lastIndex))
        }.getOrNull()?.takeIf { it.lieux.isNotEmpty() }
    }
}

/** Bande du bas de la carte en mode catégorie (maquette « 4 · Catégorie sur la carte »). */
@Composable
fun CarteCategorie(
    vue: VueCategorie,
    reference: Reference?,
    onFiche: () -> Unit,
    onItineraire: () -> Unit,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lieu = vue.choisi
    val etat = Horaires.lire(lieu.horaires)?.etat()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ouvrir la fiche", onClick = onFiche),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    lieu.nom,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val distance = reference?.let { distanceLisible(distanceMetres(it.lat, it.lon, lieu.lat, lieu.lon)) }
                Text(
                    listOfNotNull(lieu.libelle ?: vue.libelle, distance).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onItineraire,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Icon(Icones.Itineraire, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Itinéraire", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                FilledTonalButton(onClick = onFiche, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(26.dp)) {
                    Text("Fiche", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            if (vue.lieux.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BoutonIcone(
                        Icones.Precedent, "Résultat précédent", { onIndex(vue.index - 1) },
                        taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.secondaryContainer,
                        encre = MaterialTheme.colorScheme.onSurface, ombre = false,
                        actif = vue.index > 0,
                    )
                    Text(
                        "${vue.index + 1} sur ${vue.lieux.size}" + if (vue.index == 0) " · au plus près" else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    BoutonIcone(
                        Icones.Suivant, "Résultat suivant", { onIndex(vue.index + 1) },
                        taille = 44.dp, rayon = 22.dp, fond = MaterialTheme.colorScheme.secondaryContainer,
                        encre = MaterialTheme.colorScheme.onSurface, ombre = false,
                        actif = vue.index < vue.lieux.lastIndex,
                    )
                }
            }
        }
    }
}
