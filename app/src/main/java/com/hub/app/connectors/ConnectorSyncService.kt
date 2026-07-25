package com.hub.app.connectors

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hub.app.MainActivity
import com.hub.app.R
import com.hub.app.connectors.imap.ImapConnector
import com.hub.app.connectors.matrix.MatrixConnector
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.di.ServiceLocator

/**
 * Hält die Sync-Loops der API-Connectoren (Telegram/Matrix/IMAP) am Leben, auch wenn die
 * App-Activity geschlossen ist. Ohne Foreground-Service beendet Android den Prozess und
 * damit die Polling-Coroutinen – der `NotificationListenerService` ist davon nicht
 * betroffen (den hält das System selbst).
 *
 * Android verlangt für einen dauerhaften Service eine sichtbare (leise) Benachrichtigung
 * ("Hub läuft"). Auf Android 14+ zusätzlich einen Foreground-Service-Typ (`dataSync`).
 */
class ConnectorSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // ConnectorRegistry.start ist idempotent – doppelte Starts sind unschädlich.
        val registry = ServiceLocator.connectorRegistry(this)
        if (ServiceLocator.telegramConnector(this).isConfigured()) registry.start(TelegramBotConnector.SOURCE_KEY)
        if (ServiceLocator.matrixConnector(this).isConfigured()) registry.start(MatrixConnector.SOURCE_KEY)
        if (ServiceLocator.imapConnector(this).isConfigured()) registry.start(ImapConnector.SOURCE_KEY)
        // START_STICKY: Nach einem Kill durch das System den Service neu starten.
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceLocator.connectorRegistry(this).stopAll()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ensureChannel(this)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setContentTitle("Hub empfängt Nachrichten")
            .setContentText("Telegram / Matrix / E-Mail im Hintergrund")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hintergrund-Empfang", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Zeigt, dass Hub Connectoren im Hintergrund abfragt"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "hub_sync"
        private const val NOTIFICATION_ID = 42

        fun anyConnectorConfigured(context: Context): Boolean =
            ServiceLocator.telegramConnector(context).isConfigured() ||
                ServiceLocator.matrixConnector(context).isConfigured() ||
                ServiceLocator.imapConnector(context).isConfigured()

        /** Startet den Service (nur wenn Hintergrund-Empfang aktiv und ein Connector eingerichtet). */
        fun startIfEnabled(context: Context) {
            val settings = com.hub.app.notification.NotificationSettings(context)
            if (!settings.backgroundSyncEnabled) return
            if (!anyConnectorConfigured(context)) return
            // Aus dem Hintergrund kann startForegroundService (Android 12+) verboten sein
            // -> abfangen; der Start aus der Activity/nach Setup deckt den Normalfall ab.
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ConnectorSyncService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectorSyncService::class.java))
        }
    }
}
