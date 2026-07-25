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

        val isPriority = source?.isPriority == true
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
                priority = isPriority,
                isContentRedacted = message.isContentRedacted,
                hasQuickReply = message.hasQuickReply,
                iconUri = message.iconUri
            )
        )
    }

    fun observeInbox(): Flow<List<MessageEntity>> = messageDao.observeInbox()
    fun observeArchived(): Flow<List<MessageEntity>> = messageDao.observeArchived()
    fun observeUnread(): Flow<List<MessageEntity>> = messageDao.observeUnread()
    fun observeBySource(sourceKey: String): Flow<List<MessageEntity>> = messageDao.observeBySource(sourceKey)
    fun observePriorityHub(): Flow<List<MessageEntity>> = messageDao.observePriorityHub()
    fun observeSources(): Flow<List<SourceAppEntity>> = sourceAppDao.observeAll()
    fun observePriorityContacts(): Flow<List<PriorityContactEntity>> = priorityContactDao.observeAll()

    suspend fun markRead(id: String) = messageDao.markRead(id)
    suspend fun archive(id: String) = messageDao.archive(id)
    suspend fun unarchive(id: String) = messageDao.unarchive(id)
    suspend fun setPriority(id: String, priority: Boolean) = messageDao.setPriority(id, priority)
    suspend fun getById(id: String): MessageEntity? = messageDao.getById(id)

    suspend fun registerSource(sourceApp: SourceAppEntity) {
        // Existierende Werte (enabled/isPriority) nicht überschreiben, falls die Quelle
        // bereits vom Nutzer konfiguriert wurde.
        val existing = sourceAppDao.getBySourceKey(sourceApp.sourceKey)
        sourceAppDao.upsert(existing ?: sourceApp)
    }

    suspend fun setSourceEnabled(sourceKey: String, enabled: Boolean) = sourceAppDao.setEnabled(sourceKey, enabled)
    suspend fun setSourcePriority(sourceKey: String, isPriority: Boolean) = sourceAppDao.setPriority(sourceKey, isPriority)
    suspend fun isSourceEnabled(sourceKey: String): Boolean = sourceAppDao.isEnabled(sourceKey) ?: true

    suspend fun addPriorityContact(sourceKey: String, senderMatch: String) =
        priorityContactDao.insert(PriorityContactEntity(sourceKey = sourceKey, senderMatch = senderMatch, createdAt = System.currentTimeMillis()))

    suspend fun removePriorityContact(contact: PriorityContactEntity) = priorityContactDao.delete(contact)
}
