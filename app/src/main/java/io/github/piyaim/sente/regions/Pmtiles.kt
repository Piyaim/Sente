package io.github.piyaim.sente.regions

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** En-tête d'une archive PMTiles version 3 (127 octets, spécification Protomaps). */
data class EnTetePmtiles(
    val zoomMin: Int,
    val zoomMax: Int,
    val ouest: Double,
    val sud: Double,
    val est: Double,
    val nord: Double,
) {
    val latCentre: Double get() = (sud + nord) / 2
    val lonCentre: Double get() = (ouest + est) / 2
}

object Pmtiles {
    private const val TAILLE_EN_TETE = 127

    /** Lit l'en-tête ; renvoie null si le fichier n'est pas une archive PMTiles v3. */
    fun lireEnTete(fichier: File): EnTetePmtiles? {
        return try {
            RandomAccessFile(fichier, "r").use { raf ->
                if (raf.length() < TAILLE_EN_TETE) return null
                val octets = ByteArray(TAILLE_EN_TETE)
                raf.readFully(octets)
                analyser(octets)
            }
        } catch (e: java.io.IOException) {
            null
        }
    }

    fun analyser(octets: ByteArray): EnTetePmtiles? {
        if (octets.size < TAILLE_EN_TETE) return null
        if (String(octets, 0, 7, Charsets.US_ASCII) != "PMTiles") return null
        if (octets[7].toInt() != 3) return null
        val tampon = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN)
        fun degres(position: Int) = tampon.getInt(position) / 10_000_000.0
        return EnTetePmtiles(
            zoomMin = octets[100].toInt() and 0xff,
            zoomMax = octets[101].toInt() and 0xff,
            ouest = degres(102),
            sud = degres(106),
            est = degres(110),
            nord = degres(114),
        )
    }
}
