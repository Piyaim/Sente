package io.github.piyaim.sente.guidage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.piyaim.sente.ui.BoutonIcone
import io.github.piyaim.sente.ui.Icones
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.VisualInstruction

// Bandeau de manœuvre : vert forêt fixe, de jour comme de nuit, pour un contraste maximal avec le blanc.
private val VERT_BANDEAU = Color(0xFF1F4D3A)
private val VERT_SUIVANTE = Color(0xFF2C6A50)
private val FOND_ARRETER = Color(0xFFF6DCD7)
private val ENCRE_ARRETER = Color(0xFF7A1E12)

/** Écran de conduite posé sur la carte (maquette « 7 · Guidage »). L'écran reste allumé tant qu'il est affiché. */
@Composable
fun GuidageEcran(
    etat: EtatGuidage.EnCours,
    muet: Boolean,
    suivi: Boolean,
    onRecentrer: () -> Unit,
    onMuet: (Boolean) -> Unit,
    onArreter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vue = LocalView.current
    DisposableEffect(vue) {
        vue.keepScreenOn = true
        onDispose { vue.keepScreenOn = false }
    }

    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BandeauManoeuvre(etat)
            val info = when {
                etat.recalcul -> "Nouvel itinéraire…"
                etat.simulation -> "Trajet simulé (essai)"
                else -> null
            }
            if (info != null) {
                Text(
                    info,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Vitesse(etat.vitesseKmh)
                Spacer(Modifier.weight(1f))
                if (!suivi) {
                    Button(
                        onClick = onRecentrer,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Icon(Icones.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recentrer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BoutonIcone(
                        Icones.Effacer, "Arrêter le guidage", onArreter,
                        taille = 60.dp, rayon = 30.dp, fond = FOND_ARRETER, encre = ENCRE_ARRETER, ombre = false,
                    )
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            heureArrivee(etat.dureeRestante),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 28.sp),
                        )
                        Text(
                            "${dureeLisible(etat.dureeRestante)} · ${distanceRestante(etat.distanceRestante)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BoutonIcone(
                        if (muet) Icones.Muet else Icones.HautParleur,
                        if (muet) "Réactiver la voix" else "Couper la voix",
                        { onMuet(!muet) },
                        taille = 60.dp, rayon = 30.dp, fond = MaterialTheme.colorScheme.primaryContainer,
                        encre = MaterialTheme.colorScheme.onPrimaryContainer, ombre = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun BandeauManoeuvre(etat: EtatGuidage.EnCours) {
    val consigne = etat.instruction
    Column(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))) {
        Row(
            Modifier.fillMaxWidth().background(VERT_BANDEAU).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                iconeManoeuvre(consigne),
                contentDescription = consigne?.primaryContent?.text,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    distanceManoeuvre(etat.distanceManoeuvre),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 40.sp, lineHeight = 42.sp),
                )
                Text(
                    consigne?.primaryContent?.text ?: "Suivez la route",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val suivante = etat.suivante
        if (suivante != null) {
            Row(
                Modifier.fillMaxWidth().background(VERT_SUIVANTE).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Puis", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Icon(iconeManoeuvre(suivante), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    suivante.primaryContent.text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Vitesse(kmh: Int?) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.size(72.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(kmh?.toString() ?: "–", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 30.sp))
            Text("km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Petite fenêtre flottante quand on quitte Sente pendant un guidage (maquette « 9 · Guidage en petite
 * fenêtre ») : manœuvre en haut, la carte reste visible dessous, arrivée en bas.
 */
@Composable
fun GuidagePetiteFenetre(etat: EtatGuidage, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        when (etat) {
            is EtatGuidage.EnCours -> {
                Column(
                    Modifier.fillMaxWidth().background(VERT_BANDEAU).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        iconeManoeuvre(etat.instruction),
                        contentDescription = etat.instruction?.primaryContent?.text,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        distanceManoeuvre(etat.distanceManoeuvre),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 28.sp),
                    )
                    Text(
                        etat.instruction?.primaryContent?.text ?: "Suivez la route",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${heureArrivee(etat.dureeRestante)} · ${distanceRestante(etat.distanceRestante)}",
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            }
            is EtatGuidage.Arrive -> {
                Column(
                    Modifier.fillMaxWidth().background(VERT_BANDEAU).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icones.Valide, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Text("Vous êtes arrivé", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        etat.destination.nom,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Écran d'arrivée (maquette « 8 · Arrivée »). */
@Composable
fun ArriveeCarte(
    etat: EtatGuidage.Arrive,
    onTerminer: () -> Unit,
    onFavori: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icones.Valide, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vous êtes arrivé", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        etat.destination.nom,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${dureeLisible(etat.dureeSecondes)} de trajet · ${distanceRestante(etat.distanceMetres)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTerminer, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp)) {
                    Text("Terminer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                FilledTonalButton(onClick = onFavori, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(28.dp)) {
                    Icon(Icones.Etoile, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Favori", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** Flèche correspondant à une consigne. */
fun iconeManoeuvre(consigne: VisualInstruction?): ImageVector {
    val contenu = consigne?.primaryContent ?: return Icones.ToutDroit
    return when (contenu.maneuverType) {
        ManeuverType.ARRIVE -> Icones.Arrivee
        ManeuverType.ROUNDABOUT, ManeuverType.ROTARY, ManeuverType.ROUNDABOUT_TURN,
        ManeuverType.EXIT_ROUNDABOUT, ManeuverType.EXIT_ROTARY,
        -> Icones.RondPoint
        else -> when (contenu.maneuverModifier) {
            ManeuverModifier.U_TURN -> Icones.DemiTour
            ManeuverModifier.SHARP_RIGHT, ManeuverModifier.RIGHT -> Icones.VersDroite
            ManeuverModifier.SLIGHT_RIGHT -> Icones.LegerDroite
            ManeuverModifier.SLIGHT_LEFT -> Icones.LegerGauche
            ManeuverModifier.LEFT, ManeuverModifier.SHARP_LEFT -> Icones.VersGauche
            ManeuverModifier.STRAIGHT, null -> Icones.ToutDroit
        }
    }
}
