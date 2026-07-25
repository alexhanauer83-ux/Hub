package com.hub.app.notification

import android.content.Context
import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.ReplyTarget
import com.hub.app.data.source.SourceCapability
import com.hub.app.data.source.SourceQuality

/**
 * Repräsentiert den [HubNotificationListenerService] als [MessageSource].
 *
 * Besonderheit gegenüber den API-Connectoren: Diese Quelle *pollt nichts*. Android ruft
 * den Listener push-basiert auf, sobald eine Notification erscheint; der Listener schreibt
 * direkt über das Repository (das selbst der [MessageIngestSink] ist) in die DB. `start()`
 * hat hier daher keine Ingest-Schleife, sondern nur die Prüfung, ob die Berechtigung
 * überhaupt vorliegt. Der Wert dieses Adapters liegt darin, dass die Quelle in derselben
 * Liste wie Telegram/IMAP/Matrix geführt und einheitlich behandelt werden kann.
 */
class NotificationMessageSource(
    private val context: Context
) : MessageSource {

    override val sourceKey: String = "notifications"
    override val displayName: String = "Benachrichtigungen"

    // Bewusst niedriger eingestuft als API-Connectoren: gekürzte Texte, evtl. redigierte
    // Inhalte, kein Verlauf (siehe NotificationParser-KDoc).
    override val quality: SourceQuality = SourceQuality.NOTIFICATION_FALLBACK

    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.REPLY)

    override suspend fun start(ingestSink: MessageIngestSink) {
        // Kein aktives Starten möglich: Der Service wird vom System gebunden, sobald der
        // Nutzer die Berechtigung erteilt hat. Ohne Berechtigung ist die Quelle inaktiv.
    }

    override suspend fun stop() {
        QuickReplyRegistry.clear()
    }

    fun isAvailable(): Boolean = NotificationAccess.isGranted(context)

    override suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> =
        QuickReplySender.send(context, target.messageId, text)

    /**
     * Ob für diese Nachricht *jetzt* geantwortet werden kann. Reicht nicht,
     * [com.hub.app.data.local.entity.MessageEntity.hasQuickReply] zu prüfen: Das Flag
     * beschreibt nur, dass die Notification einmal eine Reply-Action hatte – die
     * zugehörige PendingIntent kann inzwischen weg sein (siehe [QuickReplyRegistry]).
     */
    fun canReplyTo(messageId: String): Boolean = QuickReplyRegistry.get(messageId) != null
}
