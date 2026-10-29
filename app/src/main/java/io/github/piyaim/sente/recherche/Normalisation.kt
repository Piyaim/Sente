package io.github.piyaim.sente.recherche

import java.text.Normalizer

/**
 * Même normalisation que le script qui fabrique l'index (Sente-cartes, scripts/recherche.py) :
 * minuscules, sans accents, ponctuation remplacée par des espaces.
 */
object Normalisation {
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val MARQUES = Regex("\\p{M}+")

    fun normaliser(texte: String): String =
        Normalizer.normalize(
            texte.lowercase().replace("œ", "oe").replace("æ", "ae").replace("ß", "ss"),
            Normalizer.Form.NFD,
        ).replace(MARQUES, "").replace(NON_ALNUM, " ").trim()

    /** Abréviations courantes, développées avant la recherche. */
    private val ABREVIATIONS = mapOf(
        "st" to "saint", "ste" to "sainte", "av" to "avenue", "ave" to "avenue", "bd" to "boulevard",
        "bld" to "boulevard", "pl" to "place", "ch" to "chemin", "che" to "chemin", "imp" to "impasse",
        "rte" to "route", "all" to "allee", "fg" to "faubourg", "sq" to "square", "crs" to "cours",
        "qu" to "quai", "pass" to "passage",
    )

    fun jetons(texteNormalise: String): List<String> =
        texteNormalise.split(' ').filter { it.isNotEmpty() }.map { ABREVIATIONS[it] ?: it }

    /** Requête FTS : chaque mot en préfixe (« merc » trouve « mercière »), tous obligatoires. */
    fun requeteFts(jetons: List<String>): String? =
        jetons.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

    private val NUMERO = Regex("^(\\d{1,5})\\s?(bis|ter|quater|quinquies|[a-z])?\\s+(.+)$")

    /** « 12 bis rue merc » → (« 12bis », « rue merc ») ; sans numéro en tête → null. */
    fun separerNumero(texteNormalise: String): Pair<String, String>? {
        val m = NUMERO.matchEntire(texteNormalise) ?: return null
        val (num, rep, reste) = m.destructured
        return (num.trimStart('0').ifEmpty { "0" } + rep) to reste
    }
}
