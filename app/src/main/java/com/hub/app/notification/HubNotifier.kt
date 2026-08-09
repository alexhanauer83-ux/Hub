package com.hub.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.hub.app.MainActivity
import com.hub.app.R
import com.hub.app.data.source.IncomingMessage

/**
 * Postet Hubs *eigene* Benachrichtigungen, wenn der Nutzer eingestellt hat, dass Fremd-
 * Benachrichtigungen durch Hub ersetzt werden sollen (siehe
 * [NotificationSettings.replaceOtherNotifications]).
 *
 * Als MessagingStyle mit Antwort-/Gelesen-Aktion, damit **Android Auto** die Nachricht
 * vorlesen und eine Sprachantwort anbieten kann. Ton läuft über den Channel (Standard oder
 * absenderspezifisch, siehe [SoundSettings]).
 */
object HubNotifier {

    // v2, weil die Wichtigkeit eines bestehenden Channels nicht per Code aenderbar ist:
    // Der alte, lautlose Channel wird geloescht und dieser mit Ton neu angelegt.
    private const val CHANNEL_ID = "hub_messages_v2"
    private const val CHANNEL_NAME = "Hub-Nachrichten"
    private const val OLD_CHANNEL_ID = "hub_replacement"
    // Stiller Channel für die Ruhezeiten: sichtbar, aber ohne Ton/Vibration/Heads-up.
    private const val SILENT_CHANNEL_ID = "hub_messages_silent"

    fun post(context: Context, message: IncomingMessage) {
        val settings = NotificationSettings(context)
        // Stummgeschaltete Quelle: keine Hub-Benachrichtigung (Nachricht bleibt im Feed).
        if (settings.isMuted(message.sourceKey)) return
        // Einzeln stummgeschaltete Konversation (z. B. lauter Gruppenchat).
        val groupValue = message.conversationId?.takeIf { it.isNotBlank() } ?: message.sender
        if (settings.isConversationMuted(message.sourceKey, groupValue)) return

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return // ohne POST_NOTIFICATIONS zwecklos

        // Ruhezeit? Dann still zustellen (sichtbar, aber ohne Ton/Heads-up). Sonst normaler
        // Channel – ggf. mit absenderspezifischem Ton (der Ton hängt am Channel, nicht an der
        // Notification).
        val quiet = NotificationSettings(context).isInQuietHours()
        val channelId = if (quiet) {
            ensureSilentChannel(context)
        } else {
            val customSound = SoundSettings(context).soundFor(message.sourceKey, message.sender)
            ensureChannel(context, customSound)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            message.stableId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // MessagingStyle + Antwort-/Gelesen-Aktion: So kann Android Auto die Nachricht
        // vorlesen und eine Sprachantwort anbieten. Die Aktionen zielen auf HubReplyReceiver,
        // der die Antwort an die richtige Quelle weiterleitet.
        val sender = Person.Builder().setName(message.sender.ifBlank { message.sourceLabel }).build()
        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("Ich").build()
        ).setConversationTitle(message.sourceLabel)
            .addMessage(message.content, message.timestamp, sender)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setStyle(messagingStyle)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(buildReplyAction(context, message.stableId))
            .addAction(buildMarkReadAction(context, message.stableId))
            .build()

        // Eine Notification pro Nachricht (stabile ID -> Updates statt Duplikate).
        runCatching { manager.notify(message.stableId.hashCode(), notification) }
    }

    /** Antwort-Aktion mit RemoteInput – von Android Auto als Sprachantwort genutzt. */
    private fun buildReplyAction(context: Context, messageId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(HubReplyReceiver.KEY_REPLY_TEXT)
            .setLabel("Antworten")
            .build()

        val intent = Intent(context, HubReplyReceiver::class.java).apply {
            action = HubReplyReceiver.ACTION_REPLY
            putExtra(HubReplyReceiver.EXTRA_MESSAGE_ID, messageId)
        }
        // MUTABLE, weil das System die Sprach-/Texteingabe in den Intent schreibt.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("reply:$messageId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_stat_hub, "Antworten", pendingIntent)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun buildMarkReadAction(context: Context, messageId: String): NotificationCompat.Action {
        val intent = Intent(context, HubReplyReceiver::class.java).apply {
            action = HubReplyReceiver.ACTION_MARK_READ
            putExtra(HubReplyReceiver.EXTRA_MESSAGE_ID, messageId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ("read:$messageId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_hub, "Gelesen", pendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * Stellt den passenden Channel sicher und liefert dessen ID. Ohne eigenen Ton der
     * Standard-Channel; mit eigenem Ton ein pro Ton eindeutiger Channel (die ID leitet sich
     * aus der Ton-URI ab, damit gleiche Töne denselben Channel teilen).
     */
    /** Stiller Channel (IMPORTANCE_LOW: kein Ton, keine Vibration, kein Heads-up) für Ruhezeiten. */
    private fun ensureSilentChannel(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return SILENT_CHANNEL_ID
        if (manager.getNotificationChannel(SILENT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                SILENT_CHANNEL_ID,
                "$CHANNEL_NAME (Ruhezeit)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Während der Ruhezeiten leise zugestellte Nachrichten"
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        return SILENT_CHANNEL_ID
    }

    private fun ensureChannel(context: Context, soundUri: String?): String {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_ID
        // Alten, lautlosen Channel aufräumen (Importance ist nachträglich nicht änderbar).
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }

        val channelId = if (soundUri.isNullOrBlank()) CHANNEL_ID else "hub_sound_${soundUri.hashCode()}"
        if (manager.getNotificationChannel(channelId) != null) return channelId

        val channel = NotificationChannel(
            channelId,
            if (soundUri.isNullOrBlank()) CHANNEL_NAME else "$CHANNEL_NAME (eigener Ton)",
            // HIGH = Ton, Vibration und Heads-up wie eine normale Nachricht.
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Von Hub gebündelte Benachrichtigungen anderer Apps"
            enableVibration(true)
            if (!soundUri.isNullOrBlank()) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                runCatching { setSound(android.net.Uri.parse(soundUri), attrs) }
            }
        }
        manager.createNotificationChannel(channel)
        return channelId
    }
}
