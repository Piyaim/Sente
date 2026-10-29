package io.github.piyaim.sente.regions

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer

/** Dossier (privé à l'appli) où se trouvent les cartes : <id>.pmtiles + <id>.json (nom, date, origine). */
const val DOSSIER_REGIONS = "regions"

data class RegionInstallee(
    val id: String,
    val nom: String,
    val fichier: File,
    val taille: Long,
    val donnees: String?,
    val origine: String,
    val enTete: EnTetePmtiles,
)

class DepotRegions(private val context: Context) {

    /** Null si le stockage de l'appli est indisponible. */
    val dossier: File?
        get() = context.getExternalFilesDir(DOSSIER_REGIONS)?.also { it.mkdirs() }

    fun installees(): List<RegionInstallee> {
        val dossier = dossier ?: return emptyList()
        return dossier.listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }
            .orEmpty()
            .mapNotNull { fichier ->
                val id = fichier.name.removeSuffix(".pmtiles")
                val enTete = Pmtiles.lireEnTete(fichier) ?: return@mapNotNull null
                val meta = lireMeta(id)
                RegionInstallee(
                    id = id,
                    nom = meta?.optString("nom").orEmpty().ifBlank { id },
                    fichier = fichier,
                    taille = fichier.length(),
                    donnees = meta?.optString("donnees").orEmpty().ifBlank { null },
                    origine = meta?.optString("origine").orEmpty().ifBlank { "import" },
                    enTete = enTete,
                )
            }
            .sortedBy { it.nom }
    }

    fun supprimer(id: String) {
        val dossier = dossier ?: return
        File(dossier, "$id.pmtiles").delete()
        File(dossier, "$id.json").delete()
    }

    fun ecrireMeta(id: String, nom: String, donnees: String?, origine: String) {
        val dossier = dossier ?: return
        val meta = JSONObject().put("nom", nom).put("origine", origine)
        if (donnees != null) meta.put("donnees", donnees)
        File(dossier, "$id.json").writeText(meta.toString())
    }

    private fun lireMeta(id: String): JSONObject? {
        val fichier = File(dossier ?: return null, "$id.json")
        return if (fichier.exists()) runCatching { JSONObject(fichier.readText()) }.getOrNull() else null
    }

    /** Copie un fichier choisi par l'utilisateur ; aucun accès réseau. */
    suspend fun importer(uri: Uri): RegionInstallee = withContext(Dispatchers.IO) {
        val dossier = dossier ?: throw IOException("Stockage de l'appli indisponible.")
        val nomFichier = nomAffiche(uri) ?: "region.pmtiles"
        val nomLisible = nomFichier.removeSuffix(".pmtiles")
        val id = identifiant(nomLisible)
        val temporaire = File(dossier, "import.part")
        try {
            val entree = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Impossible d'ouvrir le fichier.")
            entree.use { source -> temporaire.outputStream().use { source.copyTo(it, 1 shl 20) } }
            Pmtiles.lireEnTete(temporaire)
                ?: throw IOException("Ce fichier n'est pas une carte PMTiles (version 3).")
            val destination = File(dossier, "$id.pmtiles")
            destination.delete()
            if (!temporaire.renameTo(destination)) throw IOException("Impossible d'enregistrer la carte.")
            ecrireMeta(id, nomLisible, null, "import")
            installees().first { it.id == id }
        } finally {
            temporaire.delete()
        }
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
