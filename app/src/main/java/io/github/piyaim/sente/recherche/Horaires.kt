package io.github.piyaim.sente.recherche

import java.time.LocalDateTime

/**
 * Lecture simple des horaires OpenStreetMap (clé opening_hours) : jours, plages horaires, « off », « 24/7 ».
 * Les formes plus rares (jours fériés, mois, semaines…) renvoient null : on affiche alors le texte brut,
 * plutôt qu'un état « ouvert / fermé » peut-être faux.
 */
class Horaires private constructor(
    /** Pour chaque jour (0 = lundi), les plages en minutes depuis minuit ; fin > 1440 si on déborde sur le lendemain. */
    private val semaine: List<List<IntRange>>,
) {
    data class Etat(val ouvert: Boolean, val detail: String?)

    fun etat(maintenant: LocalDateTime = LocalDateTime.now()): Etat {
        val jour = maintenant.dayOfWeek.value - 1
        val minute = maintenant.hour * 60 + maintenant.minute
        // Plage en cours aujourd'hui, ou commencée hier et débordant après minuit.
        val enCours = semaine[jour].firstOrNull { minute in it.first until it.last }
            ?: semaine[(jour + 6) % 7].firstOrNull { it.last > MINUIT && minute + MINUIT in it.first until it.last }
                ?.let { (it.first - MINUIT)..(it.last - MINUIT) }
        if (enCours != null) {
            val fin = enCours.last
            return Etat(true, if (fin >= MINUIT && semaine[(jour + 1) % 7].any { it.first == 0 }) null else "ferme à ${heure(fin)}")
        }
        for (decalage in 0..7) {
            val j = (jour + decalage) % 7
            val prochaine = semaine[j].filter { decalage > 0 || it.first > minute }.minByOrNull { it.first } ?: continue
            val quand = when (decalage) {
                0 -> ""
                1 -> "demain "
                else -> "${JOURS[j].lowercase()} "
            }
            return Etat(false, "ouvre ${quand}à ${heure(prochaine.first)}")
        }
        return Etat(false, null)
    }

    /** « Lundi » → « 07:00 – 12:00, 14:00 – 19:00 » ou « Fermé ». */
    fun lignes(): List<Pair<String, String>> = JOURS.mapIndexed { i, nom ->
        nom to semaine[i].takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { "${heure(it.first)} – ${heure(it.last)}" }
            .orEmpty()
            .ifEmpty { "Fermé" }
    }

    companion object {
        private const val MINUIT = 1440
        val JOURS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
        private val CODES = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
        private val JOURS_REGLE = Regex("^((?:mo|tu|we|th|fr|sa|su)(?:\\s*[-,]\\s*(?:mo|tu|we|th|fr|sa|su))*)\\s+(.+)$")
        private val PLAGE = Regex("^(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})$")

        fun lire(texte: String?): Horaires? {
            val source = texte?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            if (source == "24/7") return Horaires(List(7) { listOf(0..MINUIT) })
            val semaine = MutableList(7) { emptyList<IntRange>() }
            for (brute in source.split(';')) {
                val regle = brute.trim().takeIf { it.isNotEmpty() } ?: continue
                val m = JOURS_REGLE.matchEntire(regle)
                val (jours, plages) = if (m != null) {
                    (lireJours(m.groupValues[1]) ?: return null) to m.groupValues[2].trim()
                } else if (regle.first().isDigit()) {
                    (0..6).toList() to regle
                } else {
                    return null // jours fériés, mois, semaines… : non pris en charge
                }
                val intervalles = if (plages == "off" || plages == "closed") emptyList() else lirePlages(plages) ?: return null
                // Comme dans OpenStreetMap, une règle plus loin remplace les précédentes pour ses jours.
                jours.forEach { semaine[it] = intervalles }
            }
            return Horaires(semaine)
        }

        private fun lireJours(texte: String): List<Int>? {
            val jours = mutableListOf<Int>()
            for (morceau in texte.split(',')) {
                val bornes = morceau.split('-').map { CODES.indexOf(it.trim()) }
                if (bornes.any { it < 0 }) return null
                when (bornes.size) {
                    1 -> jours += bornes[0]
                    2 -> {
                        var j = bornes[0]
                        while (true) {
                            jours += j
                            if (j == bornes[1]) break
                            j = (j + 1) % 7
                        }
                    }
                    else -> return null
                }
            }
            return jours.distinct()
        }

        private fun lirePlages(texte: String): List<IntRange>? {
            val plages = mutableListOf<IntRange>()
            for (morceau in texte.split(',')) {
                val m = PLAGE.matchEntire(morceau.trim()) ?: return null
                val (h1, m1, h2, m2) = m.destructured
                val debut = h1.toInt() * 60 + m1.toInt()
                var fin = h2.toInt() * 60 + m2.toInt()
                if (debut >= MINUIT || fin > MINUIT) return null
                if (fin <= debut) fin += MINUIT // « 18:00-02:00 » : se termine le lendemain
                plages += debut..fin
            }
            return plages
        }

        private fun heure(minutes: Int): String {
            val m = minutes % MINUIT
            return "%02d:%02d".format(m / 60, m % 60)
        }
    }
}
