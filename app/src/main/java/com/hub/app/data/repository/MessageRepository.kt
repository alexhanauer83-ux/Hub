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

        // Dieselbe stableId kann mehrfach eingelesen werden (App-Updates der Notification,
        // Listener-Reconnect, IMAP-Polling). Ist der INHALT unverändert, handelt es sich um
        // eine Re-Delivery -> Nutzerzustand (gelesen/archiviert) bewahren. Hat sich der Inhalt
        // geändert, ist es eine echte neue Nachricht -> wieder als ungelesen/aktiv zeigen.
        val existing = messageDao.getById(message.stableId)
        val sourcePriority = source?.isPriority == true
        // Smarte Priorisierung: Nachrichten eines als „immer priorisieren" markierten
        // Absenders landen automatisch im Priority Hub (und tragen den Stern), ohne dass der
        // Nutzer jede einzelne Nachricht antippen muss.
        val isPriorityContact = priorityContactDao.matches(message.sourceKey, message.sender)
        val sameContent = existing != null &&
            existing.content == message.content && existing.sender == message.sender

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
                isRead = if (sameContent) existing!!.isRead else false,
                isArchived = if (sameContent) existing!!.isArchived else false,
                priority = existing?.priority ?: (sourcePriority || isPriorityContact),
                isContentRedacted = message.isContentRedacted,
                hasQuickReply = message.hasQuickReply,
                iconUri = message.iconUri,
                // Anhänge: neu gelieferte bevorzugen, sonst bereits gespeicherte behalten
                // (ein erneutes Einlesen ohne Anhang soll ein vorhandenes Bild nicht löschen).
                imageUri = message.imageUri ?: existing?.imageUri,
                audioUri = message.audioUri ?: existing?.audioUri,
                // Neue Nachricht hebt einen Snooze auf (soll ja wieder erscheinen).
                snoozeUntil = if (sameContent) existing?.snoozeUntil else null,
                subject = message.subject,
                conversationTitle = message.conversationTitle ?: existing?.conversationTitle
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
    suspend fun markUnread(id: String) = messageDao.markUnread(id)
    suspend fun archive(id: String) = messageDao.archive(id)
    suspend fun unarchive(id: String) = messageDao.unarchive(id)
    suspend fun setPriority(id: String, priority: Boolean) = messageDao.setPriority(id, priority)
    suspend fun delete(id: String) = messageDao.delete(id)
    suspend fun getById(id: String): MessageEntity? = messageDao.getById(id)
    suspend fun getByIds(ids: List<String>): List<MessageEntity> = messageDao.getByIds(ids)
    suspend fun unreadInboxIds(): List<String> = messageDao.unreadInboxIds()

    /** Stellt zuvor gelöschte Nachrichten wieder her (für „Rückgängig"). */
    suspend fun restore(messages: List<MessageEntity>) = messages.forEach { messageDao.upsert(it) }

    /**
     * Legt eine von mir gesendete Antwort als ausgehende Nachricht im selben Chatverlauf ab.
     * conversationId wird auf den Gruppenschlüssel des Originals gesetzt, damit sie im
     * Verlauf (observeConversation) korrekt einsortiert wird.
     */
    suspend fun recordOutgoing(original: MessageEntity, text: String) {
        val groupValue = original.conversationId?.takeIf { it.isNotBlank() } ?: original.sender
        messageDao.upsert(
            MessageEntity(
                id = "out:${original.sourceKey}:${System.currentTimeMillis()}:${text.hashCode()}",
                sourceKey = original.sourceKey,
                sourceLabel = original.sourceLabel,
                sourcePackageName = original.sourcePackageName,
                conversationId = groupValue,
                sender = "Du",
                content = text,
                timestamp = System.currentTimeMillis(),
                category = original.category,
                isRead = true,
                isOutgoing = true
            )
        )
    }

    suspend fun markAllRead() = messageDao.markAllRead()
    suspend fun markReadIn(ids: List<String>) = messageDao.markReadIn(ids)
    suspend fun markUnreadIn(ids: List<String>) = messageDao.markUnreadIn(ids)
    suspend fun archiveIn(ids: List<String>) = messageDao.archiveIn(ids)
    suspend fun unarchiveIn(ids: List<String>) = messageDao.unarchiveIn(ids)
    suspend fun deleteIn(ids: List<String>) = messageDao.deleteIn(ids)
    /**
     * Auto-Aufräumen gegen unbegrenztes Feed-Wachstum: löscht gelesene Nachrichten, die älter
     * als [retentionDays] Tage sind. Ungelesene bleiben immer. [retentionDays] <= 0 = aus.
     */
    suspend fun pruneRead(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
        messageDao.deleteReadOlderThan(cutoff)
    }

    suspend fun snooze(id: String, until: Long) = messageDao.snooze(id, until)
    suspend fun clearExpiredSnoozes(now: Long) = messageDao.clearExpiredSnoozes(now)
    suspend fun nextSnoozeDue(): Long? = messageDao.nextSnoozeDue()

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
