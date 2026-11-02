package io.github.piyaim.sente

import android.content.Context

/** Réglages stockés sur le téléphone uniquement. */
class Reglages(context: Context) {
    private val memoire = context.getSharedPreferences("reglages", Context.MODE_PRIVATE)

    /**
     * Fournisseur de position « réseau » d'Android, en plus du GPS. Désactivé par défaut :
     * sur un téléphone avec les services Google, ce fournisseur envoie les réseaux Wi-Fi
     * et antennes environnants à Google.
     */
    var fournisseurReseau: Boolean
        get() = memoire.getBoolean("fournisseur_reseau", false)
        set(valeur) = memoire.edit().putBoolean("fournisseur_reseau", valeur).apply()

    /** Région affichée sur la carte (identifiant), ou null pour la première installée. */
    var regionAffichee: String?
        get() = memoire.getString("region_affichee", null)
        set(valeur) = memoire.edit().putString("region_affichee", valeur).apply()

    /** Classe de véhicule pour le prix des péages : « VOITURE » ou « MOTO ». */
    var classePeage: String
        get() = memoire.getString("classe_peage", "VOITURE") ?: "VOITURE"
        set(valeur) = memoire.edit().putString("classe_peage", valeur).apply()

    /** Budget péage maximum par trajet, en centimes ; -1 : pas de budget. */
    var budgetPeage: Int
        get() = memoire.getInt("budget_peage", -1)
        set(valeur) = memoire.edit().putInt("budget_peage", valeur).apply()
}
