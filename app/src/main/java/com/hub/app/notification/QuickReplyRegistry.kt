package com.hub.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput

/**
 * Hält die Quick-Reply-Actions lebender Notifications im Speicher.
 *
 * Wichtig: Eine [PendingIntent] einer fremden App lässt sich **nicht** persistieren –
 * sie ist nur gültig, solange die zugehörige Notification existiert. Deshalb ist diese
 * Registry bewusst rein in-memory und wird beim Entfernen der Notification bzw. beim
 * Neustart des Listeners geleert. Nach einem Neustart des Geräts kann auf ältere
 * Nachrichten im Hub daher nicht mehr direkt geantwortet werden – die Nachricht bleibt
 * in Room erhalten, nur die Reply-Action nicht. Die UI muss das berücksichtigen und
 * Quick Reply nur anbieten, wenn hier ein Eintrag vorliegt.
 *
 * Die eigentliche Sendelogik lebt in [QuickReplySender] (Phase 4).
 */
object QuickReplyRegistry {

    data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        /** Der RemoteInput, in den der Antworttext geschrieben wird. */
        val replyRemoteInput: RemoteInput,
        val notificationKey: String
    ) {
        // Array-Feld ⇒ equals/hashCode manuell, sonst Referenzvergleich.
        override fun equals(other: Any?): Boolean =
            this === other || (other is ReplyAction && notificationKey == other.notificationKey)

        override fun hashCode(): Int = notificationKey.hashCode()
    }

    private val actionsByMessageId = mutableMapOf<String, ReplyAction>()

    @Synchronized
    fun register(messageId: String, action: ReplyAction) {
        actionsByMessageId[messageId] = action
    }

    @Synchronized
    fun get(messageId: String): ReplyAction? = actionsByMessageId[messageId]

    /** Entfernt alle Actions, die zu einer inzwischen verschwundenen Notification gehören. */
    @Synchronized
    fun removeByNotificationKey(notificationKey: String) {
        actionsByMessageId.entries.removeAll { it.value.notificationKey == notificationKey }
    }

    @Synchronized
    fun clear() = actionsByMessageId.clear()

    /**
     * Sucht in den Actions einer Notification die erste, die einen RemoteInput mit
     * `allowFreeFormInput` anbietet – das ist die klassische "Antworten"-Action bei
     * WhatsApp, Telegram, Signal usw.
     */
    fun findRemoteInputAction(notification: Notification): ReplyActionCandidate? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            val replyInput = remoteInputs.firstOrNull { it.allowFreeFormInput } ?: continue
            return ReplyActionCandidate(action.actionIntent ?: continue, remoteInputs, replyInput)
        }
        return null
    }

    data class ReplyActionCandidate(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val replyRemoteInput: RemoteInput
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ReplyActionCandidate && pendingIntent == other.pendingIntent)

        override fun hashCode(): Int = pendingIntent.hashCode()
    }
}
