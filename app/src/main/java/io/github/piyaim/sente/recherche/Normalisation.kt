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

    /** Mots tels que tapés, sans développer les abréviations. */
    fun mots(texteNormalise: String): List<String> = texteNormalise.split(' ').filter { it.isNotEmpty() }

    fun jetons(texteNormalise: String): List<String> = developper(mots(texteNormalise), dernierAussi = true)

    /**
     * Abréviations développées. Le dernier mot est peut-être en cours de frappe (« pl » de « plateau ») :
     * [variantes] essaie donc la requête avec et sans le développer.
     */
    private fun developper(mots: List<String>, dernierAussi: Boolean): List<String> =
        mots.mapIndexed { i, m -> if (i < mots.lastIndex || dernierAussi) ABREVIATIONS[m] ?: m else m }

    /** Une ou deux listes de jetons à essayer pour ce texte (voir [developper]). */
    fun variantes(texteNormalise: String): List<List<String>> {
        val m = mots(texteNormalise)
        return listOf(developper(m, dernierAussi = true), developper(m, dernierAussi = false)).distinct()
    }

    /** Requête FTS : chaque mot en préfixe (« merc » trouve « mercière »), tous obligatoires. */
    fun requeteFts(jetons: List<String>): String? =
        jetons.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }

    private val NUMERO_DEBUT = Regex("^(\\d{1,5})\\s?(bis|ter|quater|quinquies|[a-z])?\\s+(.+)$")
    private val NUMERO_FIN = Regex("^(.+?)\\s+(\\d{1,4})\\s?(bis|ter|quater|quinquies|[a-z])?$")

    /**
     * « 12 bis rue merc » ou « rue merc 12 bis » → (« 12bis », « rue merc ») ; sans numéro → null.
     * En fin de texte, 5 chiffres sont un code postal, pas un numéro.
     */
    fun separerNumero(texteNormalise: String): Pair<String, String>? {
        NUMERO_DEBUT.matchEntire(texteNormalise)?.let { m ->
            val (num, rep, reste) = m.destructured
            return numero(num, rep) to reste
        }
        NUMERO_FIN.matchEntire(texteNormalise)?.let { m ->
            val (reste, num, rep) = m.destructured
            return numero(num, rep) to reste
        }
        return null
    }

    private fun numero(num: String, rep: String) = num.trimStart('0').ifEmpty { "0" } + rep
}
