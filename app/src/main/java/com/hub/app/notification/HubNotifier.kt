package com.hub.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hub.app.MainActivity
import com.hub.app.R
import com.hub.app.data.source.IncomingMessage

/**
 * Postet Hubs *eigene* Benachrichtigungen, wenn der Nutzer eingestellt hat, dass Fremd-
 * Benachrichtigungen durch Hub ersetzt werden sollen (siehe
 * [NotificationSettings.replaceOtherNotifications]).
 *
 * Bewusst **lautlos** (IMPORTANCE_LOW): Der Ton der Original-App ist zu diesem Zeitpunkt
 * schon erklungen (der Listener sieht die Notification erst nach dem Alarm), eine zweite
 * Tonausgabe wäre nur störend. Tippen öffnet Hub.
 */
object HubNotifier {

    private const val CHANNEL_ID = "hub_replacement"
    private const val CHANNEL_NAME = "Hub-Nachrichten"

    fun post(context: Context, message: IncomingMessage) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return // ohne POST_NOTIFICATIONS zwecklos

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            message.stableId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setContentTitle(message.sender.ifBlank { message.sourceLabel })
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setSubText(message.sourceLabel)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Eine Notification pro Nachricht (stabile ID -> Updates statt Duplikate).
        runCatching { manager.notify(message.stableId.hashCode(), notification) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            // LOW = erscheint in der Leiste, macht aber keinen Ton (Original hat schon getönt).
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Von Hub gebündelte Benachrichtigungen anderer Apps"
        }
        manager.createNotificationChannel(channel)
    }
}
