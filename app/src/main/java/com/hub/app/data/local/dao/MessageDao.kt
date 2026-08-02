package com.hub.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hub.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Upsert
    suspend fun upsert(message: MessageEntity)

    // Posteingang: gelesene Notification-Nachrichten verschwinden (Triage-Modell),
    // ABER Nachrichten aus API-Connectoren (Matrix/Telegram/SMS, isNativeConnector=1)
    // bleiben wie echte Chats erhalten - sie verschwinden nur durch Archivieren/Loeschen.
    @Query(
        """
        SELECT m.* FROM messages m
        LEFT JOIN source_apps s ON m.sourceKey = s.sourceKey
        WHERE m.isArchived = 0 AND (m.isRead = 0 OR s.isNativeConnector = 1)
          AND (m.snoozeUntil IS NULL OR m.snoozeUntil <= (strftime('%s','now') * 1000))
        ORDER BY m.timestamp DESC
        """
    )
    fun observeInbox(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isArchived = 1 ORDER BY timestamp DESC")
    fun observeArchived(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isArchived = 0 AND isRead = 0 ORDER BY timestamp DESC")
    fun observeUnread(): Flow<List<MessageEntity>>

    // Bei Auswahl einer Quelle im Drawer: ALLE Nachrichten dieser App (gelesen + ungelesen),
    // nur Archiviertes bleibt aussen vor.
    @Query(
        """
        SELECT * FROM messages
        WHERE isArchived = 0 AND sourceKey = :sourceKey
          AND (snoozeUntil IS NULL OR snoozeUntil <= (strftime('%s','now') * 1000))
        ORDER BY timestamp DESC
        """
    )
    fun observeBySource(sourceKey: String): Flow<List<MessageEntity>>

    /**
     * Alle Nachrichten einer Unterhaltung (chronologisch, wie ein Chatverlauf).
     * Der Gruppenschlüssel ist die Konversation (conversationId) bzw. – falls leer – der
     * Absender; COALESCE(NULLIF(...)) bildet genau diese Logik in SQL ab.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE sourceKey = :sourceKey
          AND COALESCE(NULLIF(conversationId, ''), sender) = :groupValue
          AND isArchived = 0
        ORDER BY timestamp ASC
        """
    )
    fun observeConversation(sourceKey: String, groupValue: String): Flow<List<MessageEntity>>

    /** Anzahl aktiver (ungelesener, nicht archivierter) Nachrichten je Quelle - fuer die Badges im Drawer. */
    @Query("SELECT sourceKey AS sourceKey, COUNT(*) AS count FROM messages WHERE isArchived = 0 AND isRead = 0 GROUP BY sourceKey")
    fun observeSourceCounts(): Flow<List<SourceCount>>

    /** Einmaliger Schnappschuss des Posteingangs fuer das Homescreen-Widget. */
    @Query(
        """
        SELECT m.* FROM messages m
        LEFT JOIN source_apps s ON m.sourceKey = s.sourceKey
        WHERE m.isArchived = 0 AND (m.isRead = 0 OR s.isNativeConnector = 1)
          AND (m.snoozeUntil IS NULL OR m.snoozeUntil <= (strftime('%s','now') * 1000))
        ORDER BY m.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun inboxSnapshot(limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE isArchived = 0 AND isRead = 0")
    suspend fun unreadCount(): Int

    /** Volltextsuche über Absender und Inhalt (einfaches LIKE, nicht archivierte Nachrichten). */
    @Query(
        """
        SELECT * FROM messages
        WHERE isArchived = 0
          AND (sender LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT 300
        """
    )
    fun search(query: String): Flow<List<MessageEntity>>

    /**
     * Priority Hub: Nachrichten, die entweder manuell priorisiert wurden, deren Quelle
     * insgesamt priorisiert ist, oder deren Absender als priorisierter Kontakt geführt wird.
     */
    @Query(
        """
        SELECT DISTINCT m.* FROM messages m
        LEFT JOIN source_apps s ON m.sourceKey = s.sourceKey
        LEFT JOIN priority_contacts p
            ON p.sourceKey = m.sourceKey AND LOWER(p.senderMatch) = LOWER(m.sender)
        WHERE m.isArchived = 0
          AND (m.priority = 1 OR s.isPriority = 1 OR p.id IS NOT NULL)
          AND (m.snoozeUntil IS NULL OR m.snoozeUntil <= (strftime('%s','now') * 1000))
        ORDER BY m.timestamp DESC
        """
    )
    fun observePriorityHub(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    /** Mehrere Nachrichten am Stück (zum Zwischenspeichern für „Rückgängig" beim Löschen). */
    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MessageEntity>

    /** IDs aller aktuell im Posteingang ungelesenen Nachrichten (für „Rückgängig" bei Alle-gelesen). */
    @Query("SELECT id FROM messages WHERE isArchived = 0 AND isRead = 0")
    suspend fun unreadInboxIds(): List<String>

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE messages SET isRead = 0 WHERE id = :id")
    suspend fun markUnread(id: String)

    @Query("UPDATE messages SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("UPDATE messages SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: String)

    @Query("UPDATE messages SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: String, priority: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    // --- Sammelaktionen ---
    @Query("UPDATE messages SET isRead = 1 WHERE isArchived = 0 AND isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE messages SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markReadIn(ids: List<String>)

    @Query("UPDATE messages SET isRead = 0 WHERE id IN (:ids)")
    suspend fun markUnreadIn(ids: List<String>)

    @Query("UPDATE messages SET isArchived = 1 WHERE id IN (:ids)")
    suspend fun archiveIn(ids: List<String>)

    @Query("UPDATE messages SET isArchived = 0 WHERE id IN (:ids)")
    suspend fun unarchiveIn(ids: List<String>)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteIn(ids: List<String>)

    // --- Snooze ---
    @Query("UPDATE messages SET snoozeUntil = :until WHERE id = :id")
    suspend fun snooze(id: String, until: Long)

    /** Abgelaufene Snoozes aufheben (vom SnoozeReceiver aufgerufen) -> Nachricht taucht wieder auf. */
    @Query("UPDATE messages SET snoozeUntil = NULL WHERE snoozeUntil IS NOT NULL AND snoozeUntil <= :now")
    suspend fun clearExpiredSnoozes(now: Long)

    /** Nächster fälliger Snooze-Zeitpunkt (zum Planen des nächsten Alarms), sonst null. */
    @Query("SELECT MIN(snoozeUntil) FROM messages WHERE snoozeUntil IS NOT NULL")
    suspend fun nextSnoozeDue(): Long?
}
