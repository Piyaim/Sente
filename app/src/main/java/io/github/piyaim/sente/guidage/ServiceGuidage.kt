package io.github.piyaim.sente.guidage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import io.github.piyaim.sente.MainActivity
import io.github.piyaim.sente.R

/**
 * Service de premier plan pendant un guidage : Android garde alors la position et la voix actives
 * quand Sente n'est plus à l'écran, et affiche une notification permanente. Il ne fait rien d'autre :
 * le guidage lui-même tourne dans [GestionGuidage].
 *
 * La notification reste neutre (« Guidage en cours ») : ni rue, ni destination, ni heure d'arrivée.
 * Son texte se lit sur l'écran verrouillé et par les applis qui ont l'accès aux notifications.
 */
class ServiceGuidage : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRETER) {
            surArret?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        creerCanal(this)
        val notif = notification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIFICATION, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(ID_NOTIFICATION, notif)
        }
        return START_NOT_STICKY
    }

    /** Sente fermé depuis les applis récentes : on arrête le guidage, sinon la voix continuerait sans écran. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        surArret?.invoke()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        const val ID_NOTIFICATION = 7301
        private const val CANAL = "guidage"
        private const val ACTION_ARRETER = "io.github.piyaim.sente.ARRETER_GUIDAGE"

        /** Appelé par le bouton « Arrêter » de la notification. */
        @Volatile
        var surArret: (() -> Unit)? = null

        fun creerCanal(context: Context) {
            val gestionnaire = context.getSystemService(NotificationManager::class.java)
            if (gestionnaire.getNotificationChannel(CANAL) == null) {
                gestionnaire.createNotificationChannel(
                    NotificationChannel(CANAL, "Guidage", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Indique qu'un guidage est en cours, sans aucun détail du trajet"
                        setShowBadge(false)
                    },
                )
            }
        }

        fun notification(context: Context): Notification {
            val ouvrir = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val arreter = PendingIntent.getService(
                context,
                1,
                Intent(context, ServiceGuidage::class.java).setAction(ACTION_ARRETER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_guidage)
                .setContentTitle("Guidage en cours")
                .setContentText("Touchez pour revenir au trajet")
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentIntent(ouvrir)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .addAction(Notification.Action.Builder(null, "Arrêter", arreter).build())
                .build()
        }
    }
}

/** Branche le service sur Ferrostar : démarré avec le guidage, arrêté avec lui, notification tenue à jour. */
class GestionService(context: Context) : ForegroundServiceManager {
    private val contexte = context.applicationContext
    private var actif = false

    override fun startService(stopNavigation: () -> Unit) {
        ServiceGuidage.surArret = stopNavigation
        actif = true
        contexte.startForegroundService(Intent(contexte, ServiceGuidage::class.java))
    }

    override fun stopService() {
        if (!actif) return
        actif = false
        ServiceGuidage.surArret = null
        contexte.stopService(Intent(contexte, ServiceGuidage::class.java))
        contexte.getSystemService(NotificationManager::class.java).cancel(ServiceGuidage.ID_NOTIFICATION)
    }

    /** Rien à mettre à jour : la notification reste neutre pendant tout le trajet. */
    override fun onNavigationStateUpdated(state: NavigationState) = Unit
}
