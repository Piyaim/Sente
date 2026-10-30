package io.github.piyaim.sente.guidage

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Positions pour le guidage : GPS du téléphone uniquement, par le LocationManager.
 *
 * On n'utilise pas la source fournie par Ferrostar : elle choisit le fournisseur « fused » quand il
 * existe, qui passe par les services Google sur la plupart des téléphones.
 */
class PositionGuidage(context: Context) : NavigationLocationProviding {
    private val contexte = context.applicationContext
    private val gestionnaire = contexte.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // vérifiée par autorisee()
    override suspend fun lastLocation(): Location? =
        if (autorisee()) runCatching { gestionnaire.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() else null

    @SuppressLint("MissingPermission") // vérifiée par autorisee()
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        val ecoute = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            // Encore appelée par Android 10 et antérieurs : à implémenter pour éviter un plantage.
            @Deprecated("Remplacée côté Android, gardée pour les anciennes versions")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        if (autorisee() && LocationManager.GPS_PROVIDER in gestionnaire.allProviders) {
            gestionnaire.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMillis, 0f, ecoute, Looper.getMainLooper())
        }
        awaitClose { gestionnaire.removeUpdates(ecoute) }
    }

    private fun autorisee() =
        contexte.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
