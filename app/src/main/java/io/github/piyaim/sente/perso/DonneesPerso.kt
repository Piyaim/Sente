package io.github.piyaim.sente.perso

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.piyaim.sente.recherche.Lieu
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

const val LISTE_PAR_DEFAUT = "Favoris"

enum class Raccourci(val cle: String, val libelle: String) {
    DOMICILE("domicile", "Domicile"),
    TRAVAIL("travail", "Travail"),
}

data class ListeFavoris(val nom: String, val lieux: List<Lieu>)

/**
 * Favoris, listes, domicile, travail et historique : une base SQLite privée à l'appli, sur le téléphone.
 * Rien n'est envoyé ; tout s'efface d'un geste et s'exporte en JSON.
 */
class DonneesPerso(context: Context) : SQLiteOpenHelper(context, "perso.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE favoris (liste TEXT NOT NULL, cle TEXT NOT NULL, donnees TEXT NOT NULL, " +
                "cree INTEGER NOT NULL, PRIMARY KEY (liste, cle))",
        )
        db.execSQL("CREATE TABLE raccourcis (nom TEXT PRIMARY KEY, donnees TEXT NOT NULL)")
        db.execSQL("CREATE TABLE historique (cle TEXT PRIMARY KEY, donnees TEXT NOT NULL, date INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, ancienne: Int, nouvelle: Int) = Unit

    // ------------------------------------------------------------------ favoris et listes

    fun listes(): List<ListeFavoris> =
        readableDatabase.rawQuery("SELECT liste, donnees FROM favoris ORDER BY liste, cree", null).use { c ->
            val parListe = linkedMapOf<String, MutableList<Lieu>>()
            while (c.moveToNext()) {
                val lieu = Lieu.depuisJson(JSONObject(c.getString(1)))
                if (lieu != null) parListe.getOrPut(c.getString(0)) { mutableListOf() } += lieu
            }
            parListe.map { (nom, lieux) -> ListeFavoris(nom, lieux) }
                .sortedWith(compareBy({ it.nom != LISTE_PAR_DEFAUT }, { it.nom.lowercase() }))
        }

    fun nomsDeListes(): List<String> = (listes().map { it.nom } + LISTE_PAR_DEFAUT).distinct()
        .sortedWith(compareBy({ it != LISTE_PAR_DEFAUT }, { it.lowercase() }))

    fun estFavori(lieu: Lieu): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favoris WHERE cle = ? LIMIT 1", arrayOf(lieu.cle)).use { it.moveToFirst() }

    fun ajouterFavori(lieu: Lieu, liste: String) {
        writableDatabase.insertWithOnConflict(
            "favoris",
            null,
            ContentValues().apply {
                put("liste", liste.trim().ifEmpty { LISTE_PAR_DEFAUT })
                put("cle", lieu.cle)
                put("donnees", lieu.versJson().toString())
                put("cree", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** Retire le lieu de toutes les listes. */
    fun retirerFavori(lieu: Lieu) {
        writableDatabase.delete("favoris", "cle = ?", arrayOf(lieu.cle))
    }

    fun retirerDeListe(lieu: Lieu, liste: String) {
        writableDatabase.delete("favoris", "cle = ? AND liste = ?", arrayOf(lieu.cle, liste))
    }

    // ------------------------------------------------------------------ domicile et travail

    fun raccourci(r: Raccourci): Lieu? =
        readableDatabase.rawQuery("SELECT donnees FROM raccourcis WHERE nom = ?", arrayOf(r.cle)).use { c ->
            if (c.moveToFirst()) Lieu.depuisJson(JSONObject(c.getString(0))) else null
        }

    fun definirRaccourci(r: Raccourci, lieu: Lieu?) {
        if (lieu == null) {
            writableDatabase.delete("raccourcis", "nom = ?", arrayOf(r.cle))
        } else {
            writableDatabase.insertWithOnConflict(
                "raccourcis",
                null,
                ContentValues().apply { put("nom", r.cle); put("donnees", lieu.versJson().toString()) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    // ------------------------------------------------------------------ historique

    fun historique(max: Int = 20): List<Lieu> =
        readableDatabase.rawQuery("SELECT donnees FROM historique ORDER BY date DESC LIMIT $max", null).use { c ->
            buildList { while (c.moveToNext()) Lieu.depuisJson(JSONObject(c.getString(0)))?.let(::add) }
        }

    fun ajouterHistorique(lieu: Lieu) {
        val db = writableDatabase
        db.insertWithOnConflict(
            "historique",
            null,
            ContentValues().apply {
                put("cle", lieu.cle); put("donnees", lieu.versJson().toString()); put("date", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        db.execSQL("DELETE FROM historique WHERE cle NOT IN (SELECT cle FROM historique ORDER BY date DESC LIMIT $MAX_HISTORIQUE)")
    }

    fun effacerHistorique() {
        writableDatabase.delete("historique", null, null)
    }

    // ------------------------------------------------------------------ tout

    fun toutEffacer() {
        writableDatabase.apply {
            delete("favoris", null, null)
            delete("raccourcis", null, null)
            delete("historique", null, null)
            execSQL("VACUUM")
        }
    }

    fun exporterJson(): String {
        val listes = JSONArray()
        listes().forEach { liste ->
            listes.put(JSONObject().put("nom", liste.nom).put("lieux", JSONArray().apply { liste.lieux.forEach { put(it.versJson()) } }))
        }
        val racine = JSONObject()
            .put("format", 1)
            .put("application", "Sente")
            .put("exporte", Instant.now().toString())
            .put("listes", listes)
            .put("historique", JSONArray().apply { historique(MAX_HISTORIQUE).forEach { put(it.versJson()) } })
        Raccourci.entries.forEach { r -> racine.put(r.cle, raccourci(r)?.versJson() ?: JSONObject.NULL) }
        return racine.toString(2)
    }

    private companion object {
        const val MAX_HISTORIQUE = 30
    }
}
