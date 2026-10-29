package io.github.piyaim.sente.position

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/**
 * Position par le LocationManager d'Android : GPS d'abord, jamais le Fused Location Provider de Google.
 * Le fournisseur « réseau » n'est utilisé que si l'utilisateur l'a activé dans les réglages.
 */
class SourcePosition(
    context: Context,
    private val utiliserReseau: Boolean,
    private val surPosition: (Location) -> Unit,
    private val surEtatGps: (Boolean) -> Unit,
) : LocationListener {

    private val contexte = context.applicationContext
    private val gestionnaire = contexte.getSystemService(LocationManager::class.java)

    fun gpsActif(): Boolean = gestionnaire.isProviderEnabled(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission") // vérifiée juste avant
    fun demarrer() {
        if (contexte.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val principale = Looper.getMainLooper()
        if (LocationManager.GPS_PROVIDER in gestionnaire.allProviders) {
            gestionnaire.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVALLE_GPS_MS, 0f, this, principale)
            gestionnaire.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(surPosition)
        }
        if (utiliserReseau && LocationManager.NETWORK_PROVIDER in gestionnaire.allProviders) {
            gestionnaire.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVALLE_RESEAU_MS, 0f, this, principale)
        }
        surEtatGps(gpsActif())
    }

    fun arreter() {
        gestionnaire.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) = surPosition(location)

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) surEtatGps(true)
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) surEtatGps(false)
    }

    // Encore appelée par Android 10 et antérieurs : à implémenter pour éviter un plantage.
    @Deprecated("Remplacée côté Android, gardée pour les anciennes versions")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private companion object {
        const val INTERVALLE_GPS_MS = 1_000L
        const val INTERVALLE_RESEAU_MS = 10_000L
    }
}
