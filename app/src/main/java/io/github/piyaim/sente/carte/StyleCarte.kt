package io.github.piyaim.sente.carte

import android.content.Context
import java.io.File

/**
 * Styles clair et sombre générés depuis Protomaps (noms en français), avec polices et icônes
 * intégrées à l'appli : la carte s'affiche entièrement hors ligne.
 */
object StyleCarte {
    private const val ESPACE_REGION = "__FICHIER_REGION__"

    fun json(context: Context, sombre: Boolean, region: File?): String {
        if (region == null) return styleVide(sombre)
        val nom = if (sombre) "styles/sombre.json" else "styles/clair.json"
        val modele = context.assets.open(nom).bufferedReader().use { it.readText() }
        return modele.replace(ESPACE_REGION, "file://" + region.absolutePath)
    }

    private fun styleVide(sombre: Boolean): String {
        val fond = if (sombre) "#101512" else "#F3F5F1"
        return """{"version":8,"sources":{},"layers":[{"id":"fond","type":"background","paint":{"background-color":"$fond"}}]}"""
    }
}
