package io.github.piyaim.sente.recherche

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Résultats d'une recherche, par section. */
data class Resultats(val adresses: List<Lieu>, val lieux: List<Lieu>) {
    val vide: Boolean get() = adresses.isEmpty() && lieux.isEmpty()
}

/** Point de référence pour trier par distance (position, sinon centre de la carte). */
data class Reference(val lat: Double, val lon: Double)

/**
 * Lecture de l'index de recherche d'une région (fichier SQLite fabriqué par Sente-cartes).
 * Lecture seule ; toutes les fonctions sont à appeler hors du fil principal.
 */
class IndexRecherche private constructor(private val db: SQLiteDatabase) : Closeable {

    fun chercher(texte: String, ref: Reference, max: Int = 10): Resultats {
        val normalise = Normalisation.normaliser(texte)
        if (normalise.isEmpty()) return Resultats(emptyList(), emptyList())

        val numero = Normalisation.separerNumero(normalise)
        val adresses = mutableListOf<Lieu>()
        val voies = Normalisation.variantes(numero?.second ?: normalise)
            .flatMap { jetons -> Normalisation.requeteFts(jetons)?.let { voies(it, jetons) }.orEmpty() }
            .distinctBy { it.id }
            .sortedBy { distanceMetres(ref.lat, ref.lon, it.lieu.lat, it.lieu.lon) }
            .take(8)
        if (numero != null) {
            voies.mapNotNullTo(adresses) { adresse(it, numero.first) }
        }
        if (adresses.size < 3) adresses += voies.take(5 - adresses.size).map { it.lieu }

        // « boulangerie », « pharmacies »… : les plus proches de la catégorie, plutôt qu'un tirage dans toute la région.
        val categorie = categories[normalise] ?: categories[normalise.removeSuffix("s")]
        val lieux = if (categorie != null) {
            parCategorie(categorie, ref, max)
        } else {
            Normalisation.variantes(normalise)
                .flatMap { jetons -> Normalisation.requeteFts(jetons)?.let { lieux(it, ref, normalise, max) }.orEmpty() }
                .distinctBy { it.cle }
                .sortedWith(ordreLieux(ref, normalise))
                .take(max)
        }
        return Resultats(adresses.distinctBy { it.cle }.take(max), lieux)
    }

    /** Lieux d'une catégorie OSM (« bakery »…) autour du point, du plus proche au plus lointain. */
    fun parCategorie(code: String, ref: Reference, max: Int = 30): List<Lieu> {
        val sql = "SELECT $COLONNES_LIEU FROM lieux WHERE code = ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 2000"
        var rayon = 0.03 // degrés de latitude, environ 3 km ; élargi tant qu'il y a trop peu de résultats
        var trouves = emptyList<Lieu>()
        for (essai in 1..4) {
            trouves = db.rawQuery(
                sql,
                arrayOf(code, e6(ref.lat - rayon), e6(ref.lat + rayon), e6(ref.lon - rayon * 1.5), e6(ref.lon + rayon * 1.5)),
            ).use { c -> c.liste { lireLieu(it) } }
            if (trouves.size >= 15) break
            rayon *= 3
        }
        return trouves.sortedBy { distanceMetres(ref.lat, ref.lon, it.lat, it.lon) }.take(max)
    }

    override fun close() = db.close()

    /** Libellé normalisé → code de catégorie (« boulangerie » → « bakery »), lu une fois dans l'index. */
    private val categories: Map<String, String> by lazy {
        db.rawQuery("SELECT DISTINCT code, libelle FROM lieux WHERE code != 'commune'", null).use { c ->
            buildMap { while (c.moveToNext()) put(Normalisation.normaliser(c.getString(1)), c.getString(0)) }
        }
    }

    // ------------------------------------------------------------------ voies et adresses

    private class Voie(val id: Long, val lieu: Lieu, val commune: String, val cp: String)

    /**
     * Rues correspondant à la requête. Au moins un mot doit toucher le nom de la rue : taper « lyon » seul
     * ne doit pas sortir des rues au hasard parce que leur commune s'appelle Lyon.
     */
    private fun voies(requete: String, jetons: List<String>): List<Voie> =
        db.rawQuery(
            "SELECT id, nom, commune, cp, lat, lon FROM voies WHERE id IN " +
                "(SELECT docid FROM voies_fts WHERE voies_fts MATCH ? LIMIT 1500)",
            arrayOf(requete),
        ).use { c ->
            c.liste {
                val commune = it.getString(2)
                val cp = it.getString(3)
                Voie(
                    id = it.getLong(0),
                    commune = commune,
                    cp = cp,
                    lieu = Lieu(
                        type = TypeLieu.RUE,
                        nom = it.getString(1),
                        sousTitre = "Rue entière · " + communeLisible(cp, commune),
                        lat = it.getLong(4) / 1e6,
                        lon = it.getLong(5) / 1e6,
                    ),
                )
            }
        }.filter { voie ->
            val mots = Normalisation.normaliser(voie.lieu.nom).split(' ')
            jetons.any { j -> mots.any { it.startsWith(j) } && j !in MOTS_VIDES }
        }

    private fun adresse(voie: Voie, numero: String): Lieu? =
        db.rawQuery("SELECT dlat, dlon FROM numeros WHERE voie = ? AND num = ?", arrayOf(voie.id.toString(), numero))
            .use { c ->
                if (!c.moveToFirst()) null else Lieu(
                    type = TypeLieu.ADRESSE,
                    nom = "$numero ${voie.lieu.nom}",
                    sousTitre = communeLisible(voie.cp, voie.commune),
                    lat = voie.lieu.lat + c.getLong(0) / 1e5,
                    lon = voie.lieu.lon + c.getLong(1) / 1e5,
                    adresse = "$numero ${voie.lieu.nom}, ${communeLisible(voie.cp, voie.commune)}",
                )
            }

    // ------------------------------------------------------------------ lieux

    private fun lieux(requete: String, ref: Reference, normalise: String, max: Int): List<Lieu> =
        db.rawQuery(
            "SELECT $COLONNES_LIEU FROM lieux WHERE id IN (SELECT docid FROM lieux_fts WHERE lieux_fts MATCH ? LIMIT 2000)",
            arrayOf(requete),
        ).use { c -> c.liste { lireLieu(it) } }
            .sortedWith(ordreLieux(ref, normalise))
            .take(max)

    /** Un nom qui commence par ce qu'on a tapé passe devant ; puis communes ; puis le plus proche. */
    private fun ordreLieux(ref: Reference, normalise: String) = compareBy<Lieu>(
        { if (Normalisation.normaliser(it.nom).startsWith(normalise)) 0 else 1 },
        { if (it.type == TypeLieu.COMMUNE) 0 else 1 },
        { distanceMetres(ref.lat, ref.lon, it.lat, it.lon) },
    )

    private fun lireLieu(c: Cursor): Lieu {
        val code = c.getString(1)
        val commune = code == "commune"
        val adresse = c.getStringOrNull(5)
        return Lieu(
            type = if (commune) TypeLieu.COMMUNE else TypeLieu.LIEU,
            nom = c.getString(0),
            sousTitre = if (commune) listOfNotNull(adresse, "Commune").joinToString(" · ") else c.getString(2),
            lat = c.getLong(3) / 1e6,
            lon = c.getLong(4) / 1e6,
            code = code,
            libelle = c.getString(2),
            adresse = if (commune) null else adresse,
            horaires = c.getStringOrNull(6),
            telephone = c.getStringOrNull(7),
            site = c.getStringOrNull(8),
            fauteuil = c.getStringOrNull(9),
            osm = c.getStringOrNull(10),
        )
    }

    companion object {
        private const val FORMAT = "1"

        /** Mots trop courants pour désigner une rue à eux seuls. */
        private val MOTS_VIDES = setOf("rue", "de", "du", "des", "la", "le", "les", "l", "d", "avenue", "boulevard", "place", "chemin", "route", "impasse", "allee")
        private const val COLONNES_LIEU = "nom, code, libelle, lat, lon, adresse, horaires, tel, site, fauteuil, osm"
        private val ENTETE_SQLITE = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        fun ouvrir(fichier: File): IndexRecherche? {
            if (!estSqlite(fichier)) return null
            return try {
                val db = SQLiteDatabase.openDatabase(fichier.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                if (meta(db)["format"] == FORMAT) {
                    IndexRecherche(db)
                } else {
                    db.close()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /** Table « meta » d'un fichier d'index (région, date…), ou null si ce n'est pas un index Sente. */
        fun lireMeta(fichier: File): Map<String, String>? {
            if (!estSqlite(fichier)) return null
            return try {
                SQLiteDatabase.openDatabase(fichier.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    meta(db).takeIf { it["format"] == FORMAT }
                }
            } catch (e: Exception) {
                null
            }
        }

        fun estSqlite(fichier: File): Boolean {
            return try {
                RandomAccessFile(fichier, "r").use { raf ->
                    if (raf.length() < 100) {
                        false
                    } else {
                        val octets = ByteArray(ENTETE_SQLITE.size)
                        raf.readFully(octets)
                        octets.contentEquals(ENTETE_SQLITE)
                    }
                }
            } catch (e: java.io.IOException) {
                false
            }
        }

        private fun meta(db: SQLiteDatabase): Map<String, String> =
            db.rawQuery("SELECT cle, valeur FROM meta", null).use { c ->
                buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
            }

        private fun e6(v: Double) = Math.round(v * 1e6).toString()

        private fun communeLisible(cp: String, commune: String) = listOf(cp, commune).filter { it.isNotBlank() }.joinToString(" ")
    }
}

private inline fun <T> Cursor.liste(lire: (Cursor) -> T): List<T> {
    val resultat = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) resultat += lire(this)
    return resultat
}

private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i).ifBlank { null }
