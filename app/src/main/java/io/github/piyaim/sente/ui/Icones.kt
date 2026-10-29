package io.github.piyaim.sente.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icônes au trait des maquettes (grille 24, trait 2,2). Dessinées en noir puis teintées par Icon().
 */
object Icones {
    private fun trait(nom: String, epaisseur: Float = 2.2f, vararg chemins: String): ImageVector =
        ImageVector.Builder(nom, 24.dp, 24.dp, 24f, 24f).apply {
            chemins.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = epaisseur,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun cercle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    val Recherche = trait("recherche", 2.2f, cercle(11f, 11f, 7f), "M20,20 L16.2,16.2")
    val Calques = trait("calques", 2f, "M12,3 L21,8 L12,13 L3,8 Z", "M3,12.5 L12,17.5 L21,12.5", "M3,16.5 L12,21.5 L21,16.5")
    val Domicile = trait("domicile", 2.2f, "M4,11 L12,4 L20,11 V20 H4 Z")
    val Travail = trait("travail", 2.2f, "M5,7 H19 A2,2 0 0 1 21,9 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 V9 A2,2 0 0 1 5,7 Z", "M9,7 V4 H15 V7")
    val Etoile = trait("etoile", 2.2f, "M12,3.5 L14.6,9 L20.5,9.6 L16,13.6 L17.3,19.5 L12,16.5 L6.7,19.5 L8,13.6 L3.5,9.6 L9.4,9 Z")
    val Viseur = trait("viseur", 2.2f, cercle(12f, 12f, 6.5f), cercle(12f, 12f, 1.2f), "M12,2 V5", "M12,19 V22", "M2,12 H5", "M19,12 H22")
    val Retour = trait("retour", 2.2f, "M15,5 L8,12 L15,19")
    val Effacer = trait("effacer", 2.4f, "M6,6 L18,18", "M18,6 L6,18")
    val Repere = trait("repere", 2.2f, "M12,21 C12,21 5,14.5 5,9.5 A7,7 0 0 1 19,9.5 C19,14.5 12,21 12,21 Z", cercle(12f, 9.5f, 2.4f))
    val Rue = trait("rue", 2.2f, "M7,3 L5,21", "M17,3 L19,21", "M12,5 V8", "M12,11 V14", "M12,17 V20")
    val Horloge = trait("horloge", 2f, cercle(12f, 12f, 8.5f), "M12,7.5 V12 L15,14")
    val Telephone = trait(
        "telephone", 2f,
        "M6,3.5 H9.5 L11,8 L8.8,9.6 C9.8,11.9 12.1,14.2 14.4,15.2 L16,13 L20.5,14.5 V18 C20.5,19.4 19.4,20.5 18,20.5 C10.5,20.5 3.5,13.5 3.5,6 C3.5,4.6 4.6,3.5 6,3.5 Z",
    )
    val Globe = trait("globe", 2f, cercle(12f, 12f, 8.5f), "M3.5,12 H20.5", "M12,3.5 C9,6.5 9,17.5 12,20.5 C15,17.5 15,6.5 12,3.5 Z")
    val Partager = trait("partager", 2.2f, "M12,3.5 V14.5", "M7.5,8 L12,3.5 L16.5,8", "M5,12.5 V19.5 H19 V12.5")
    val Fauteuil = trait("fauteuil", 2f, cercle(12f, 4.5f, 1.8f), "M12,7.5 V13 H17 L19,19", "M8.5,10.5 A5.5,5.5 0 1 0 15.5,18")
    val Itineraire = trait("itineraire", 2.2f, "M5,19 L5,13 C5,10 7,9 10,9 L18,9", "M14,5 L18,9 L14,13")
    val Supprimer = trait("supprimer", 2f, "M4,7 H20", "M9,7 V4 H15 V7", "M6,7 L7,20 H17 L18,7")
    val Valide = trait("valide", 2.2f, "M5,12.5 L10,17.5 L19,7")
}
