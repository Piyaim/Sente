package io.github.piyaim.sente.regions

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.piyaim.sente.itineraire.MoteurItineraire
import io.github.piyaim.sente.recherche.IndexRecherche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer

/**
 * Dossier (privé à l'appli) des régions. Pour une région <id> :
 * <id>.pmtiles (carte), <id>.recherche.db (index de recherche, facultatif),
 * <id>.routage.tar (tuiles d'itinéraire, facultatif), <id>.json (nom, dates, origine).
 */
const val DOSSIER_REGIONS = "regions"

enum class TypeFichier(val suffixe: String, val libelle: String) {
    CARTE(".pmtiles", "carte"),
    RECHERCHE(".recherche.db", "recherche"),
    ROUTAGE(".routage.tar", "itinéraire"),
}

data class RegionInstallee(
    val id: String,
    val nom: String,
    val fichier: File,
    val taille: Long,
    val donnees: String?,
    val origine: String,
    val enTete: EnTetePmtiles,
    val recherche: File?,
    val rechercheDonnees: String?,
    val routage: File? = null,
    val routageDonnees: String? = null,
) {
    val tailleRecherche: Long get() = recherche?.length() ?: 0L
    val tailleRoutage: Long get() = routage?.length() ?: 0L
}

class DepotRegions(private val context: Context) {

    /** Null si le stockage de l'appli est indisponible. */
    val dossier: File?
        get() = context.getExternalFilesDir(DOSSIER_REGIONS)?.also { it.mkdirs() }

    fun fichier(id: String, type: TypeFichier): File? = dossier?.let { File(it, id + type.suffixe) }

    fun installees(): List<RegionInstallee> {
        val dossier = dossier ?: return emptyList()
        return dossier.listFiles { f -> f.isFile && f.name.endsWith(TypeFichier.CARTE.suffixe) }
            .orEmpty()
            .mapNotNull { fichier ->
                val id = fichier.name.removeSuffix(TypeFichier.CARTE.suffixe)
                val enTete = Pmtiles.lireEnTete(fichier) ?: return@mapNotNull null
                val meta = lireMeta(id)
                val recherche = File(dossier, id + TypeFichier.RECHERCHE.suffixe).takeIf { it.isFile }
                val routage = File(dossier, id + TypeFichier.ROUTAGE.suffixe).takeIf { it.isFile }
                RegionInstallee(
                    id = id,
                    nom = meta?.optString("nom").orEmpty().ifBlank { id },
                    fichier = fichier,
                    taille = fichier.length(),
                    donnees = meta?.optString("donnees").orEmpty().ifBlank { null },
                    origine = meta?.optString("origine").orEmpty().ifBlank { "import" },
                    enTete = enTete,
                    recherche = recherche,
                    rechercheDonnees = meta?.optString("recherche_donnees").orEmpty().ifBlank { null },
                    routage = routage,
                    routageDonnees = meta?.optString("routage_donnees").orEmpty().ifBlank { null },
                )
            }
            .sortedBy { it.nom }
    }

    fun supprimer(id: String) {
        TypeFichier.entries.forEach { fichier(id, it)?.delete() }
        dossier?.let { File(it, "$id.json").delete() }
    }

    /** Met à jour les informations d'une région ; une valeur null laisse l'ancienne en place. */
    fun majMeta(
        id: String,
        nom: String? = null,
        donnees: String? = null,
        origine: String? = null,
        rechercheDonnees: String? = null,
        routageDonnees: String? = null,
    ) {
        val dossier = dossier ?: return
        val meta = lireMeta(id) ?: JSONObject()
        nom?.let { meta.put("nom", it) }
        donnees?.let { meta.put("donnees", it) }
        origine?.let { meta.put("origine", it) }
        rechercheDonnees?.let { meta.put("recherche_donnees", it) }
        routageDonnees?.let { meta.put("routage_donnees", it) }
        File(dossier, "$id.json").writeText(meta.toString())
    }

    private fun lireMeta(id: String): JSONObject? {
        val fichier = File(dossier ?: return null, "$id.json")
        return if (fichier.exists()) runCatching { JSONObject(fichier.readText()) }.getOrNull() else null
    }

    /**
     * Copie un fichier choisi par l'utilisateur (carte .pmtiles, index de recherche .db ou tuiles
     * d'itinéraire <région>.routage.tar), sans accès réseau.
     * Renvoie un message pour l'utilisateur.
     */
    suspend fun importer(uri: Uri): String = withContext(Dispatchers.IO) {
        val dossier = dossier ?: throw IOException("Stockage de l'appli indisponible.")
        val nomFichier = nomAffiche(uri) ?: "region"
        val temporaire = File(dossier, "import.part")
        try {
            val entree = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Impossible d'ouvrir le fichier.")
            entree.use { source -> temporaire.outputStream().use { source.copyTo(it, 1 shl 20) } }

            if (Pmtiles.lireEnTete(temporaire) != null) {
                val nomLisible = nomFichier.substringBeforeLast('.')
                val id = identifiant(nomLisible)
                remplacer(temporaire, File(dossier, id + TypeFichier.CARTE.suffixe))
                majMeta(id, nom = lireMeta(id)?.optString("nom")?.ifBlank { null } ?: nomLisible, origine = "import")
                return@withContext "Carte importée : $nomLisible"
            }

            // Les tuiles d'itinéraire ne disent pas leur région : on la lit dans le nom « <région>.routage.tar ».
            if (MoteurItineraire.estTuilesValhalla(temporaire)) {
                val id = nomFichier.removeSuffix(TypeFichier.ROUTAGE.suffixe).takeIf { nomFichier.endsWith(TypeFichier.ROUTAGE.suffixe) && idValide(it) }
                    ?: throw IOException("Tuiles d'itinéraire : gardez le nom d'origine du fichier (<région>.routage.tar).")
                remplacer(temporaire, File(dossier, id + TypeFichier.ROUTAGE.suffixe))
                majMeta(id)
                val nom = lireMeta(id)?.optString("nom")?.ifBlank { null } ?: id
                return@withContext "Itinéraire importé pour $nom"
            }

            val meta = IndexRecherche.lireMeta(temporaire)
                ?: throw IOException("Ce fichier n'est ni une carte PMTiles, ni un index de recherche, ni des tuiles d'itinéraire Sente.")
            val id = meta["region"]?.takeIf { idValide(it) }
                ?: throw IOException("Index de recherche sans région valide.")
            remplacer(temporaire, File(dossier, id + TypeFichier.RECHERCHE.suffixe))
            majMeta(id, rechercheDonnees = meta["donnees"])
            val nom = lireMeta(id)?.optString("nom")?.ifBlank { null } ?: id
            if (fichier(id, TypeFichier.CARTE)?.exists() == true) {
                "Recherche importée pour $nom"
            } else {
                "Recherche importée pour $nom : importez aussi sa carte pour l'utiliser."
            }
        } finally {
            temporaire.delete()
        }
    }

    private fun remplacer(source: File, destination: File) {
        destination.delete()
        if (!source.renameTo(destination)) throw IOException("Impossible d'enregistrer le fichier.")
    }

    private fun nomAffiche(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { curseur -> if (curseur.moveToFirst()) curseur.getString(0) else null }

    companion object {
        private val ID_VALIDE = Regex("[a-z0-9]+(-[a-z0-9]+)*")

        fun idValide(id: String) = ID_VALIDE.matches(id)

        /** « Auvergne-Rhône-Alpes » → « auvergne-rhone-alpes ». */
        fun identifiant(nom: String): String =
            Normalizer.normalize(nom, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "region-importee" }

        fun sha256(fichier: File): String {
            val empreinte = MessageDigest.getInstance("SHA-256")
            fichier.inputStream().use { flux ->
                val tampon = ByteArray(1 shl 20)
                while (true) {
                    val lus = flux.read(tampon)
                    if (lus < 0) break
                    empreinte.update(tampon, 0, lus)
                }
            }
            return empreinte.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
