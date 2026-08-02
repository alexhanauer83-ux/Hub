package com.hub.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kategorie einer Nachricht. Wird von den [com.hub.app.data.source.MessageSource]-Implementierungen
 * beim Ingest vergeben (z. B. anhand der Notification-Kategorie oder des Connector-Typs).
 */
enum class MessageCategory {
    MESSAGING,
    EMAIL,
    SOCIAL,
    CALL,
    SMS,
    OTHER
}

/**
 * Einheitliches Nachrichten-Objekt, in das alle Quellen (NotificationListenerService,
 * SMS-Provider, API-Connectoren) einspeisen. `id` ist ein stabiler, quellenübergreifend
 * eindeutiger Schlüssel (siehe [com.hub.app.data.source.IncomingMessage.stableId]), damit
 * Upserts bei erneuten/aktualisierten Notifications keine Duplikate erzeugen.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["sourceKey"]),
        Index(value = ["timestamp"]),
        Index(value = ["isArchived", "isRead"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    /** z. B. "notif:com.whatsapp", "sms", "telegram_bot", "imap:privat", "matrix" */
    val sourceKey: String,
    /** Anzeigename der Quelle, z. B. "WhatsApp" */
    val sourceLabel: String,
    /** Paketname der Quell-App, sofern über Notification erfasst (für Icon-Lookup) */
    val sourcePackageName: String?,
    /** Gruppiert zusammengehörige Nachrichten (Konversation/Chat) für Quick Reply */
    val conversationId: String?,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val category: MessageCategory,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    /** Manuell oder regelbasiert gesetzte Priorität (siehe Priority Hub) */
    val priority: Boolean = false,
    /** true, wenn Android wegen "Sensible Inhalte ausblenden" nur Platzhaltertext lieferte */
    val isContentRedacted: Boolean = false,
    /** true, wenn zu dieser Nachricht eine RemoteInput-Quick-Reply-Action existiert */
    val hasQuickReply: Boolean = false,
    val iconUri: String? = null,
    /** Lokale file://-URI eines Bildanhangs (aus der Benachrichtigung extrahiert), sonst null. */
    val imageUri: String? = null,
    /** Lokale file://-URI eines Audioanhangs, sofern die App ihn mitgeliefert hat, sonst null. */
    val audioUri: String? = null,
    /** Zurückgestellt ("snooze") bis zu diesem Zeitpunkt (epoch ms); solange aus dem Feed ausgeblendet. */
    val snoozeUntil: Long? = null,
    /** true = von mir gesendete Antwort (im Chatverlauf rechts dargestellt). */
    val isOutgoing: Boolean = false,
    /** Betreff (v. a. E-Mail), separat vom Textkörper in [content]. */
    val subject: String? = null,
    /** Lesbarer Titel der Unterhaltung (z. B. Matrix-Raumname), unabhängig vom Gruppenschlüssel. */
    val conversationTitle: String? = null
)
