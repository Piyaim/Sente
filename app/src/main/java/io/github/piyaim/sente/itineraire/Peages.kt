package io.github.piyaim.sente.itineraire

import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.recherche.distanceMetres
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Classe de péage française retenue pour les prix (indice dans les tarifs : 0 = classe 1, 4 = classe 5). */
enum class ClassePeage(val indice: Int, val libelle: String) {
    VOITURE(0, "Voiture"),
    MOTO(4, "Moto"),
}

/** Une cabine de péage réellement traversée par le trajet (trouvée par Valhalla). */
data class PassagePeage(val lat: Double, val lon: Double, val metres: Double, val route: String?)

/** Une section payante : entre une entrée et une sortie (réseau fermé), ou une barrière (réseau ouvert). */
data class SectionPeage(
    val entree: String,
    val sortie: String?,
    val route: String?,
    val operateur: String,
    val metres: Double?,
    /** Prix en centimes ; null si la base ne le connaît pas. */
    val centimes: Int?,
    /** Point à éviter pour contourner cette section (la gare d'entrée ou la barrière). */
    val evitement: Reference,
    /** Barrière d'un réseau ouvert (prix fixe), plutôt qu'une entrée et une sortie. */
    val ouverte: Boolean = false,
)

/** Péages d'un trajet : sections, total connu, et si des cabines n'ont pas pu être reliées à la base. */
data class CoutPeages(
    val sections: List<SectionPeage>,
    val totalCentimes: Int,
    val complet: Boolean,
    /** Gares traversées, pour les repères jaunes sur la carte. */
    val gares: List<Reference> = emptyList(),
) {
    val gratuit: Boolean get() = sections.isEmpty() && complet
}

/**
 * Tarifs des péages d'une région (fichier `<région>.peages.json` fabriqué par Sente-cartes à partir
 * d'OpenTollData, licence ODbL). Prix indicatifs : les tarifs changent chaque 1er février.
 */
class TarifsPeages private constructor(
    val region: String,
    /** Date des tarifs, « 20260403 ». */
    val dateTarifs: String,
    private val gares: List<Gare>,
    private val sections: Map<Long, IntArray>,
    private val ouvertes: Map<Int, IntArray>,
) {
    private class Gare(val nom: String, val lat: Double, val lon: Double, val ouverte: Boolean, val operateur: String)

    /** Relie les cabines traversées aux gares de la base, puis calcule chaque section pour la classe voulue. */
    fun cout(passages: List<PassagePeage>, classe: ClassePeage): CoutPeages {
        var complet = true
        // Cabine → gare la plus proche (moins de 400 m) ; plusieurs cabines d'une même gare comptent une fois.
        val traversees = mutableListOf<Triple<Int, PassagePeage, Double>>()
        for (passage in passages) {
            val (indice, distance) = gares.withIndex()
                .map { (i, g) -> i to distanceMetres(passage.lat, passage.lon, g.lat, g.lon) }
                .minByOrNull { it.second } ?: (-1 to Double.MAX_VALUE)
            if (indice < 0 || distance > DISTANCE_MAX_M) {
                // Cabine inconnue sur une autoroute : le total est peut-être incomplet. Ailleurs
                // (barrière de parking, par exemple), on l'ignore.
                if (passage.route != null) complet = false
                continue
            }
            if (traversees.lastOrNull()?.first != indice) traversees += Triple(indice, passage, distance)
        }

        val resultat = mutableListOf<SectionPeage>()
        var entree: Pair<Int, PassagePeage>? = null
        for ((indice, passage) in traversees) {
            val gare = gares[indice]
            if (gare.ouverte) {
                val prix = ouvertes[indice]?.get(classe.indice)
                if (prix == null) complet = false
                resultat += SectionPeage(joli(gare.nom), null, passage.route, gare.operateur, null, prix, Reference(gare.lat, gare.lon), ouverte = true)
                continue
            }
            val debut = entree
            if (debut == null) {
                entree = indice to passage
            } else {
                val (iEntree, pEntree) = debut
                val prix = sections[cle(iEntree, indice)]?.get(classe.indice)
                if (prix == null) complet = false
                val g = gares[iEntree]
                resultat += SectionPeage(
                    entree = joli(g.nom),
                    sortie = joli(gare.nom),
                    route = pEntree.route ?: passage.route,
                    operateur = g.operateur,
                    metres = passage.metres - pEntree.metres,
                    centimes = prix,
                    evitement = Reference(g.lat, g.lon),
                )
                entree = null
            }
        }
        // Entrée sans sortie retrouvée : section signalée, prix inconnu.
        entree?.let { (i, p) ->
            complet = false
            val g = gares[i]
            resultat += SectionPeage(joli(g.nom), null, p.route, g.operateur, null, null, Reference(g.lat, g.lon))
        }
        return CoutPeages(
            resultat,
            resultat.sumOf { it.centimes ?: 0 },
            complet,
            traversees.map { (i, _) -> Reference(gares[i].lat, gares[i].lon) },
        )
    }

    companion object {
        private const val FORMAT = 1
        private const val DISTANCE_MAX_M = 400.0

        private fun cle(a: Int, b: Int): Long = a.toLong() * 1_000_000L + b

        fun lire(fichier: File): TarifsPeages? = runCatching {
            val j = JSONObject(fichier.readText())
            if (j.optInt("format") != FORMAT || j.optString("source") != "OpenTollData") return null
            val tg = j.getJSONArray("gares")
            val gares = (0 until tg.length()).map { i ->
                val g = tg.getJSONObject(i)
                Gare(g.getString("n"), g.getDouble("lat"), g.getDouble("lon"), g.optString("t") == "o", g.optString("op"))
            }
            val ts = j.getJSONArray("sections")
            val sections = HashMap<Long, IntArray>(ts.length() * 2)
            for (i in 0 until ts.length()) {
                val s = ts.getJSONArray(i)
                val p = s.getJSONArray(3)
                val prix = IntArray(p.length()) { p.getInt(it) }
                val a = s.getInt(0)
                val b = s.getInt(1)
                sections[cle(a, b)] = prix
                sections.putIfAbsent(cle(b, a), prix) // tarifs symétriques quand le sens inverse manque
            }
            val to = j.getJSONObject("ouvertes")
            val ouvertes = to.keys().asSequence().associate { k ->
                val p = to.getJSONArray(k)
                k.toInt() to IntArray(p.length()) { p.getInt(it) }
            }
            TarifsPeages(j.getString("region"), j.optString("tarifs"), gares, sections, ouvertes)
        }.getOrNull()

        /** Région d'un fichier de péages Sente, ou null si ce n'en est pas un (import manuel). */
        fun lireRegion(fichier: File): String? = runCatching {
            if (fichier.length() > 20_000_000) return null
            val j = JSONObject(fichier.readText())
            j.getString("region").takeIf { j.optString("source") == "OpenTollData" && j.optInt("format") == FORMAT }
        }.getOrNull()

        /** « ST QUENTIN FAL BARRIERE » → « St Quentin Fal Barriere ». */
        private fun joli(nom: String): String =
            nom.lowercase(Locale.FRANCE).split(' ').joinToString(" ") { mot ->
                if (mot in PETITS_MOTS) mot else mot.replaceFirstChar { it.titlecase(Locale.FRANCE) }
            }.replaceFirstChar { it.titlecase(Locale.FRANCE) }

        private val PETITS_MOTS = setOf("de", "du", "des", "la", "le", "les", "d", "l", "sur", "en", "et", "a")
    }
}

/** « 13,60 € ». */
fun prixLisible(centimes: Int): String = "%,.2f €".format(Locale.FRANCE, centimes / 100.0)
