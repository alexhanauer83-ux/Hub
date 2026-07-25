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

    // Posteingang zeigt bewusst nur ungelesene, nicht archivierte Nachrichten - gelesene
    // verschwinden aus der Uebersicht (Triage-Modell). Der vollstaendige Verlauf einer
    // App ist weiterhin ueber observeBySource (Drawer-Auswahl) erreichbar.
    @Query("SELECT * FROM messages WHERE isArchived = 0 AND isRead = 0 ORDER BY timestamp DESC")
    fun observeInbox(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isArchived = 1 ORDER BY timestamp DESC")
    fun observeArchived(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isArchived = 0 AND isRead = 0 ORDER BY timestamp DESC")
    fun observeUnread(): Flow<List<MessageEntity>>

    // Bei Auswahl einer Quelle im Drawer: ALLE Nachrichten dieser App (gelesen + ungelesen),
    // nur Archiviertes bleibt aussen vor.
    @Query("SELECT * FROM messages WHERE isArchived = 0 AND sourceKey = :sourceKey ORDER BY timestamp DESC")
    fun observeBySource(sourceKey: String): Flow<List<MessageEntity>>

    /** Anzahl aktiver (ungelesener, nicht archivierter) Nachrichten je Quelle - fuer die Badges im Drawer. */
    @Query("SELECT sourceKey AS sourceKey, COUNT(*) AS count FROM messages WHERE isArchived = 0 AND isRead = 0 GROUP BY sourceKey")
    fun observeSourceCounts(): Flow<List<SourceCount>>

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
        ORDER BY m.timestamp DESC
        """
    )
    fun observePriorityHub(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE messages SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("UPDATE messages SET isArchived = 0 WHERE id = :id")
    suspend fun unarchive(id: String)

    @Query("UPDATE messages SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: String, priority: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)
}
