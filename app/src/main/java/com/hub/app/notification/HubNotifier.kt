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

    // v2, weil die Wichtigkeit eines bestehenden Channels nicht per Code aenderbar ist:
    // Der alte, lautlose Channel wird geloescht und dieser mit Ton neu angelegt.
    private const val CHANNEL_ID = "hub_messages_v2"
    private const val CHANNEL_NAME = "Hub-Nachrichten"
    private const val OLD_CHANNEL_ID = "hub_replacement"

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
            // Ton + Vibration, damit Hub wie eine normale Benachrichtigung alarmiert.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Eine Notification pro Nachricht (stabile ID -> Updates statt Duplikate).
        runCatching { manager.notify(message.stableId.hashCode(), notification) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Alten, lautlosen Channel aufräumen (Importance ist nachträglich nicht änderbar).
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            // HIGH = Ton, Vibration und Heads-up wie eine normale Nachricht.
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Von Hub gebündelte Benachrichtigungen anderer Apps"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
