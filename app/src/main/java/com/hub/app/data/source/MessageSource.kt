package com.hub.app.data.source

import com.hub.app.data.local.entity.MessageCategory

/**
 * Gemeinsame Architektur-Schnittstelle für alle Nachrichtenquellen der App.
 *
 * Zwei grundsätzlich verschiedene Arten von Quellen implementieren dieses Interface:
 *  1. [com.hub.app.notification.NotificationMessageSource] – repräsentiert den
 *     `NotificationListenerService` (Kernfunktion 1). Ingestion erfolgt push-basiert:
 *     Android ruft den Listener auf, wenn eine Notification erscheint; `start()`
 *     registriert dafür lediglich den [MessageIngestSink] am Listener.
 *  2. API-Connectoren wie `TelegramBotConnector`, `ImapConnector`, `MatrixConnector`
 *     (Kernfunktion 2) – Ingestion erfolgt aktiv (Polling/Streaming) innerhalb von
 *     `start()`.
 *
 * Beide Arten speisen über [MessageIngestSink.ingest] in denselben Room-Feed ein,
 * wodurch die UI nie wissen muss, woher eine Nachricht stammt.
 */
interface MessageSource {
    /** Stabiler, eindeutiger Schlüssel, z. B. "notifications", "telegram_bot", "imap:privat". */
    val sourceKey: String

    val displayName: String

    /** API-Connectoren liefern volle Historie und gelten als zuverlässiger als der Notification-Fallback. */
    val quality: SourceQuality

    val capabilities: Set<SourceCapability>

    /** Startet die Quelle. Für push-basierte Quellen nur Registrierung, für Connectoren aktives Polling/Login. */
    suspend fun start(ingestSink: MessageIngestSink)

    suspend fun stop()

    suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("$displayName unterstützt keine Antworten"))
}

enum class SourceQuality {
    /** Notification-Abgriff: schnittstellenlos, aber limitiert (z. B. Platzhaltertext bei verstecktem Inhalt). */
    NOTIFICATION_FALLBACK,
    /** Direkte API-/SDK-Anbindung: voller Verlauf, zuverlässiger. */
    API_NATIVE
}

enum class SourceCapability {
    REPLY,
    MARK_READ,
    FULL_HISTORY
}

data class ReplyTarget(
    val messageId: String,
    val conversationId: String?
)

/**
 * Rohdaten, die eine [MessageSource] für eine einzelne Nachricht liefert, bevor sie
 * in eine [com.hub.app.data.local.entity.MessageEntity] übersetzt und upserted wird.
 */
data class IncomingMessage(
    val sourceKey: String,
    val sourceLabel: String,
    val sourcePackageName: String?,
    /** ID der Nachricht innerhalb der Quelle (z. B. Notification-Key, Telegram update_id). */
    val externalId: String,
    val conversationId: String?,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val category: MessageCategory,
    val isContentRedacted: Boolean = false,
    val hasQuickReply: Boolean = false,
    val iconUri: String? = null
) {
    /** Quellenübergreifend eindeutiger Room-Primärschlüssel. */
    val stableId: String get() = "$sourceKey:$externalId"
}

/** Senke, in die jede [MessageSource] ihre Nachrichten einspeist – implementiert von [com.hub.app.data.repository.MessageRepository]. */
interface MessageIngestSink {
    suspend fun ingest(message: IncomingMessage)
}
