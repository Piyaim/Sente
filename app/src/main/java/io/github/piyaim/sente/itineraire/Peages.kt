package io.github.piyaim.sente.itineraire

import io.github.piyaim.sente.recherche.Reference
import io.github.piyaim.sente.recherche.distanceMetres
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Classe de péage française retenue pour les prix (indice dans les tarifs : 0 = classe 1, 4 = classe 5). */
enum class ClassePeage(val indice: Int, val libelle: String) {
    VOITURE(0, "Voiture"),
    MOTO(4, "Moto"),
}

/**
 * Une cabine de péage réellement traversée par le trajet (trouvée par Valhalla), ou la fin de la
 * dernière portion payante. [voieRapide] : autoroute ou voie rapide (pas une barrière de parking).
 */
data class PassagePeage(
    val lat: Double,
    val lon: Double,
    val metres: Double,
    val route: String?,
    val nom: String? = null,
    val voieRapide: Boolean = false,
)

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
    /** Prix déduit de la sortie connue la plus proche, faute de tarif exact. */
    val estime: Boolean = false,
    /** Péage absent de la base de tarifs (périphérique, tunnel…) : prix inconnu. */
    val horsBase: Boolean = false,
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
    val estime: Boolean get() = sections.any { it.estime }

    /** « ≈ 13,60 € », « ≈ 9,00 € (estimé) », « ≥ 13,60 € » ou « prix inconnu ». */
    val prixAffiche: String
        get() = when {
            totalCentimes == 0 && !complet -> "prix inconnu"
            !complet -> "≥ " + prixLisible(totalCentimes)
            estime -> "≈ " + prixLisible(totalCentimes) + " (estimé)"
            else -> "≈ " + prixLisible(totalCentimes)
        }
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
    /** Pour chaque gare, les gares reliées par un tarif connu (dans un sens ou l'autre). */
    private val reliees: Map<Int, IntArray> = sections.keys
        .groupBy({ (it / 1_000_000L).toInt() }, { (it % 1_000_000L).toInt() })
        .mapValues { (_, v) -> v.toIntArray() }

    private class Gare(val nom: String, val lat: Double, val lon: Double, val ouverte: Boolean, val operateur: String)

    /**
     * Relie les cabines traversées aux gares de la base, puis calcule chaque section pour la classe voulue.
     * [finPayante] : fin de la dernière portion payante, pour une entrée dont la sortie n'a pas été vue.
     */
    fun cout(passages: List<PassagePeage>, classe: ClassePeage, finPayante: PassagePeage? = null): CoutPeages {
        var complet = true
        val resultat = mutableListOf<SectionPeage>()
        val reperes = mutableListOf<Reference>()
        var entree: Pair<Int, PassagePeage>? = null
        var derniere = -1
        for (passage in passages) {
            // Cabine → gare la plus proche (moins de 400 m) ; plusieurs cabines d'une même gare comptent une fois.
            val (indice, distance) = plusProche(passage.lat, passage.lon)
            if (indice < 0 || distance > DISTANCE_MAX_M) {
                // Péage absent de la base sur une voie rapide (périphérique, tunnel…) : signalé, prix inconnu.
                // Ailleurs (barrière de parking, par exemple), on l'ignore.
                if (passage.voieRapide) {
                    complet = false
                    val ici = Reference(passage.lat, passage.lon)
                    reperes += ici
                    resultat += SectionPeage(passage.nom ?: passage.route ?: "Péage", null, passage.route, "", null, null, ici, ouverte = true, horsBase = true)
                }
                continue
            }
            if (indice == derniere) continue
            derniere = indice
            val gare = gares[indice]
            reperes += Reference(gare.lat, gare.lon)
            if (gare.ouverte) {
                val prix = ouvertes[indice]?.get(classe.indice)
                if (prix == null) complet = false
                resultat += SectionPeage(joli(gare.nom), null, passage.route, gare.operateur, null, prix, Reference(gare.lat, gare.lon), ouverte = true)
                continue
            }
            val debut = entree
            if (debut == null) {
                entree = indice to passage
                continue
            }
            entree = null
            val (iEntree, pEntree) = debut
            val prix = prixExact(iEntree, indice, classe)
            val estimation = if (prix == null) estimer(iEntree, gare.lat, gare.lon, classe) else null
            if (prix == null && estimation == null) complet = false
            val g = gares[iEntree]
            resultat += SectionPeage(
                entree = joli(g.nom),
                sortie = joli(gare.nom),
                route = pEntree.route ?: passage.route,
                operateur = g.operateur,
                metres = passage.metres - pEntree.metres,
                centimes = prix ?: estimation,
                evitement = Reference(g.lat, g.lon),
                estime = estimation != null,
            )
        }
        // Entrée sans sortie retrouvée : prix estimé jusqu'à la fin de la portion payante, sinon inconnu.
        entree?.let { (i, p) ->
            val g = gares[i]
            val fin = finPayante?.takeIf { it.metres > p.metres + 1000 }
            val estimation = fin?.let { estimer(i, it.lat, it.lon, classe) }
            if (estimation == null) complet = false
            resultat += SectionPeage(
                joli(g.nom), null, p.route, g.operateur, fin?.let { it.metres - p.metres }, estimation,
                Reference(g.lat, g.lon), estime = estimation != null,
            )
        }
        return CoutPeages(resultat, resultat.sumOf { it.centimes ?: 0 }, complet, reperes)
    }

    private fun plusProche(lat: Double, lon: Double): Pair<Int, Double> =
        gares.withIndex()
            .map { (i, g) -> i to distanceMetres(lat, lon, g.lat, g.lon) }
            .minByOrNull { it.second } ?: (-1 to Double.MAX_VALUE)

    /** Gares de même société et de même type à moins de 3 km : souvent la même gare sous deux noms. */
    private fun voisines(i: Int): List<Int> {
        val g = gares[i]
        return gares.indices.filter { j ->
            val h = gares[j]
            j != i && h.operateur == g.operateur && h.ouverte == g.ouverte &&
                distanceMetres(g.lat, g.lon, h.lat, h.lon) <= DISTANCE_VOISINE_M
        }
    }

    /** Tarif de la base pour cette paire de gares, ou pour une paire de gares voisines. */
    private fun prixExact(entree: Int, sortie: Int, classe: ClassePeage): Int? {
        val entrees = listOf(entree) + voisines(entree)
        val sorties = listOf(sortie) + voisines(sortie)
        for (e in entrees) for (s in sorties) {
            sections[cle(e, s)]?.getOrNull(classe.indice)?.takeIf { it > 0 }?.let { return it }
        }
        return null
    }

    /**
     * Prix estimé de l'entrée [entree] jusqu'au point visé : tarif de la sortie connue la plus proche
     * de ce point, ramené à la distance (à vol d'oiseau) depuis l'entrée. Écart médian mesuré : 4 %.
     */
    private fun estimer(entree: Int, lat: Double, lon: Double, classe: ClassePeage): Int? {
        val e = gares[entree]
        val km = distanceMetres(e.lat, e.lon, lat, lon) / 1000
        if (km < 1) return null
        var meilleure = -1
        var ecart = Double.MAX_VALUE
        for (s in reliees[entree] ?: return null) {
            val prix = sections[cle(entree, s)]?.getOrNull(classe.indice) ?: continue
            if (prix <= 0 || distanceMetres(e.lat, e.lon, gares[s].lat, gares[s].lon) < 3000) continue
            val d = distanceMetres(lat, lon, gares[s].lat, gares[s].lon)
            if (d < ecart) {
                ecart = d
                meilleure = s
            }
        }
        if (meilleure < 0 || ecart > ECART_MAX_M) return null
        val reference = gares[meilleure]
        val kmReference = distanceMetres(e.lat, e.lon, reference.lat, reference.lon) / 1000
        val prix = sections.getValue(cle(entree, meilleure))[classe.indice]
        return (prix * km / kmReference / 10).roundToInt() * 10 // arrondi aux 10 centimes
    }

    companion object {
        private const val FORMAT = 1
        private const val DISTANCE_MAX_M = 400.0
        private const val DISTANCE_VOISINE_M = 3000.0
        private const val ECART_MAX_M = 40_000.0

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
