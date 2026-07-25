package com.hub.app.data.repository

import com.hub.app.data.local.dao.MessageDao
import com.hub.app.data.local.dao.PriorityContactDao
import com.hub.app.data.local.dao.SourceAppDao
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.data.local.entity.PriorityContactEntity
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.source.IncomingMessage
import com.hub.app.data.source.MessageIngestSink
import kotlinx.coroutines.flow.Flow

/**
 * Zentrale Senke für alle [com.hub.app.data.source.MessageSource]-Implementierungen und
 * gleichzeitig die einzige Datenquelle, die die UI beobachtet (Single Source of Truth).
 */
class MessageRepository(
    private val messageDao: MessageDao,
    private val sourceAppDao: SourceAppDao,
    private val priorityContactDao: PriorityContactDao
) : MessageIngestSink {

    override suspend fun ingest(message: IncomingMessage) {
        val source = sourceAppDao.getBySourceKey(message.sourceKey)
        if (source != null && !source.enabled) return // Nutzer hat diese Quelle deaktiviert

        // Dieselbe Benachrichtigung wird vom Listener mehrfach eingelesen (App-Updates der
        // Notification, erneutes Verbinden des Services). Da die stableId gleich bleibt,
        // wuerde ein naives Upsert den vom Nutzer gesetzten Zustand (gelesen/archiviert/
        // priorisiert) jedes Mal ueberschreiben. Deshalb den bestehenden Zustand bewahren.
        val existing = messageDao.getById(message.stableId)
        val sourcePriority = source?.isPriority == true

        messageDao.upsert(
            MessageEntity(
                id = message.stableId,
                sourceKey = message.sourceKey,
                sourceLabel = message.sourceLabel,
                sourcePackageName = message.sourcePackageName,
                conversationId = message.conversationId,
                sender = message.sender,
                content = message.content,
                timestamp = message.timestamp,
                category = message.category,
                isRead = existing?.isRead ?: false,
                isArchived = existing?.isArchived ?: false,
                priority = existing?.priority ?: sourcePriority,
                isContentRedacted = message.isContentRedacted,
                hasQuickReply = message.hasQuickReply,
                iconUri = message.iconUri,
                // Anhänge: neu gelieferte bevorzugen, sonst bereits gespeicherte behalten
                // (ein erneutes Einlesen ohne Anhang soll ein vorhandenes Bild nicht löschen).
                imageUri = message.imageUri ?: existing?.imageUri,
                audioUri = message.audioUri ?: existing?.audioUri
            )
        )
    }

    fun observeInbox(): Flow<List<MessageEntity>> = messageDao.observeInbox()
    fun observeArchived(): Flow<List<MessageEntity>> = messageDao.observeArchived()
    fun observeUnread(): Flow<List<MessageEntity>> = messageDao.observeUnread()
    fun observeBySource(sourceKey: String): Flow<List<MessageEntity>> = messageDao.observeBySource(sourceKey)
    fun observeConversation(sourceKey: String, groupValue: String): Flow<List<MessageEntity>> =
        messageDao.observeConversation(sourceKey, groupValue)
    fun observePriorityHub(): Flow<List<MessageEntity>> = messageDao.observePriorityHub()
    fun observeSources(): Flow<List<SourceAppEntity>> = sourceAppDao.observeAll()
    fun observeSourceCounts(): Flow<List<com.hub.app.data.local.dao.SourceCount>> = messageDao.observeSourceCounts()
    fun search(query: String): Flow<List<MessageEntity>> = messageDao.search(query)
    suspend fun inboxSnapshot(limit: Int): List<MessageEntity> = messageDao.inboxSnapshot(limit)
    suspend fun unreadCount(): Int = messageDao.unreadCount()
    fun observePriorityContacts(): Flow<List<PriorityContactEntity>> = priorityContactDao.observeAll()

    suspend fun markRead(id: String) = messageDao.markRead(id)
    suspend fun archive(id: String) = messageDao.archive(id)
    suspend fun unarchive(id: String) = messageDao.unarchive(id)
    suspend fun setPriority(id: String, priority: Boolean) = messageDao.setPriority(id, priority)
    suspend fun delete(id: String) = messageDao.delete(id)
    suspend fun getById(id: String): MessageEntity? = messageDao.getById(id)

    suspend fun registerSource(sourceApp: SourceAppEntity) {
        val existing = sourceAppDao.getBySourceKey(sourceApp.sourceKey)
        when {
            existing == null -> sourceAppDao.upsert(sourceApp)
            // Label nachträglich verbessern: Wurde die Quelle früher nur mit dem Paketnamen
            // erfasst (App-Name damals nicht auflösbar) und liegt jetzt ein echter Name vor,
            // aktualisieren - Nutzer-Einstellungen (enabled/isPriority) bleiben erhalten.
            existing.label != sourceApp.label && sourceApp.label != sourceApp.packageName ->
                sourceAppDao.upsert(existing.copy(label = sourceApp.label))
            // Sonst bestehende, vom Nutzer konfigurierte Quelle unverändert lassen.
        }
    }

    suspend fun setSourceEnabled(sourceKey: String, enabled: Boolean) = sourceAppDao.setEnabled(sourceKey, enabled)
    suspend fun setSourcePriority(sourceKey: String, isPriority: Boolean) = sourceAppDao.setPriority(sourceKey, isPriority)
    suspend fun isSourceEnabled(sourceKey: String): Boolean = sourceAppDao.isEnabled(sourceKey) ?: true

    suspend fun addPriorityContact(sourceKey: String, senderMatch: String) =
        priorityContactDao.insert(PriorityContactEntity(sourceKey = sourceKey, senderMatch = senderMatch, createdAt = System.currentTimeMillis()))

    suspend fun removePriorityContact(contact: PriorityContactEntity) = priorityContactDao.delete(contact)
}
