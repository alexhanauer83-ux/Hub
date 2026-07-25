package com.hub.app.ui.hub

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.data.source.ReplyTarget
import com.hub.app.di.ServiceLocator
import com.hub.app.notification.ContentIntentRegistry
import com.hub.app.notification.NotificationAccess
import com.hub.app.notification.NotificationMessageSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Aktive Feed-Ansicht. POSTEINGANG zeigt nur ungelesene, nicht archivierte Nachrichten
 * (gelesene verschwinden); die Quellen-Auswahl (Drawer) läuft separat über
 * [HubViewModel.sourceFilter] und zeigt dann alle Nachrichten der gewählten App.
 */
enum class HubTab { POSTEINGANG, PRIORITAET, ARCHIV }

/** Referenz auf eine Unterhaltung (Chat/Kontakt). */
data class ConversationRef(val sourceKey: String, val groupValue: String, val title: String)

/** Zusammenfassung einer Unterhaltung für die gruppierte Liste. */
data class ConversationSummary(
    val sourceKey: String,
    val groupValue: String,
    val title: String,
    val sourceLabel: String,
    val packageName: String?,
    val latestContent: String,
    val latestTimestamp: Long,
    val total: Int,
    val unread: Int,
    val isRedacted: Boolean,
    val hasImage: Boolean
) {
    val ref get() = ConversationRef(sourceKey, groupValue, title)
}

/** Der Gruppenschlüssel einer Nachricht: Konversationstitel, sonst Absender. */
fun MessageEntity.groupValue(): String = conversationId?.takeIf { it.isNotBlank() } ?: sender

data class HubUiState(
    val messages: List<MessageEntity> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val sources: List<SourceAppEntity> = emptyList(),
    /** sourceKey -> Anzahl aktiver Nachrichten, für die Badges im Drawer. */
    val sourceCounts: Map<String, Int> = emptyMap(),
    val tab: HubTab = HubTab.POSTEINGANG,
    val sourceFilter: String? = null,
    val conversationFilter: ConversationRef? = null,
    val grouped: Boolean = true,
    val searchQuery: String? = null,
    val hasNotificationAccess: Boolean = false
) {
    val isSearching: Boolean get() = searchQuery != null

    /** Gruppierte Übersicht anzeigen? Nur im Posteingang ohne aktive Filter/Suche. */
    val showConversations: Boolean
        get() = grouped && !isSearching && conversationFilter == null &&
            sourceFilter == null && tab == HubTab.POSTEINGANG
}

private data class FeedFilter(
    val tab: HubTab,
    val sourceFilter: String?,
    val conversationFilter: ConversationRef?,
    val grouped: Boolean,
    val searchQuery: String?
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val notificationSource = NotificationMessageSource(application)

    private val _tab = MutableStateFlow(HubTab.POSTEINGANG)
    val tab: StateFlow<HubTab> = _tab.asStateFlow()

    private val _quickReplyState = MutableStateFlow<QuickReplyState>(QuickReplyState.Idle)
    val quickReplyState: StateFlow<QuickReplyState> = _quickReplyState.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)
    private val _conversationFilter = MutableStateFlow<ConversationRef?>(null)
    private val _grouped = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow<String?>(null)
    private val _hasNotificationAccess = MutableStateFlow(NotificationAccess.isGranted(application))

    private val filter = combine(
        _tab, _sourceFilter, _conversationFilter, _grouped, _searchQuery
    ) { tab, src, conv, grouped, query ->
        FeedFilter(tab, src, conv, grouped, query)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = filter.flatMapLatest { f ->
        val query = f.searchQuery?.trim()
        when {
            !query.isNullOrBlank() -> repository.search(query)
            f.conversationFilter != null ->
                repository.observeConversation(f.conversationFilter.sourceKey, f.conversationFilter.groupValue)
            f.sourceFilter != null -> repository.observeBySource(f.sourceFilter)
            f.tab == HubTab.PRIORITAET -> repository.observePriorityHub()
            f.tab == HubTab.ARCHIV -> repository.observeArchived()
            else -> repository.observeInbox()
        }
    }

    // Gruppierte Übersicht: aus dem Posteingang zu Unterhaltungen zusammengefasst.
    private val conversations = repository.observeInbox().map { groupIntoConversations(it) }

    private val sourceCounts = repository.observeSourceCounts()

    val uiState: StateFlow<HubUiState> = combine(
        messages,
        conversations,
        repository.observeSources(),
        combine(sourceCounts, _hasNotificationAccess) { counts, access -> counts to access },
        filter
    ) { messages, conversations, sources, (counts, access), f ->
        HubUiState(
            messages = messages,
            conversations = conversations,
            sources = sources,
            sourceCounts = counts.associate { it.sourceKey to it.count },
            tab = f.tab,
            sourceFilter = f.sourceFilter,
            conversationFilter = f.conversationFilter,
            grouped = f.grouped,
            searchQuery = f.searchQuery,
            hasNotificationAccess = access
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    private fun groupIntoConversations(messages: List<MessageEntity>): List<ConversationSummary> =
        messages.groupBy { it.sourceKey to it.groupValue() }
            .map { (key, msgs) ->
                // observeInbox liefert bereits nach Zeit absteigend -> erstes = neuestes.
                val latest = msgs.first()
                ConversationSummary(
                    sourceKey = key.first,
                    groupValue = key.second,
                    title = latest.groupValue(),
                    sourceLabel = latest.sourceLabel,
                    packageName = latest.sourcePackageName,
                    latestContent = latest.content,
                    latestTimestamp = latest.timestamp,
                    total = msgs.size,
                    unread = msgs.count { !it.isRead },
                    isRedacted = latest.isContentRedacted,
                    hasImage = latest.imageUri != null
                )
            }
            .sortedByDescending { it.latestTimestamp }

    fun selectTab(tab: HubTab) {
        _tab.value = tab
        _conversationFilter.value = null
    }

    fun selectSourceFilter(sourceKey: String?) {
        _sourceFilter.value = sourceKey
        _conversationFilter.value = null
    }

    fun openConversation(ref: ConversationRef) { _conversationFilter.value = ref }
    fun closeConversation() { _conversationFilter.value = null }
    fun setGrouped(grouped: Boolean) { _grouped.value = grouped }

    fun startSearch() { _searchQuery.value = "" }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun stopSearch() { _searchQuery.value = null }

    /** Nach Rückkehr aus den Systemeinstellungen erneut prüfen. */
    fun refreshNotificationAccess() {
        _hasNotificationAccess.value = NotificationAccess.isGranted(getApplication())
    }

    /**
     * Priorisiert dauerhaft alle künftigen Nachrichten dieses Absenders in dieser Quelle
     * (Priority Hub), nicht nur die angetippte Nachricht.
     */
    fun addPriorityContact(message: MessageEntity) = viewModelScope.launch {
        repository.addPriorityContact(message.sourceKey, message.sender)
        repository.setPriority(message.id, true)
    }

    fun setSourcePriority(sourceKey: String, isPriority: Boolean) =
        viewModelScope.launch { repository.setSourcePriority(sourceKey, isPriority) }

    /**
     * Wählt die richtige [com.hub.app.data.source.MessageSource] für die Antwort anhand des
     * Quell-Schlüssels: API-Connectoren (Matrix/Telegram) antworten über ihre API, SMS über
     * den SmsManager, alles andere (WhatsApp, Signal … via Benachrichtigung) über den
     * RemoteInput-Weg des Notification-Listeners.
     */
    private fun replySourceFor(message: MessageEntity): com.hub.app.data.source.MessageSource =
        when (message.sourceKey) {
            com.hub.app.connectors.matrix.MatrixConnector.SOURCE_KEY ->
                ServiceLocator.matrixConnector(getApplication())
            com.hub.app.connectors.telegram.TelegramBotConnector.SOURCE_KEY ->
                ServiceLocator.telegramConnector(getApplication())
            com.hub.app.sms.SmsMessageSource.SOURCE_KEY -> smsSource
            else -> notificationSource
        }

    private val smsSource by lazy { com.hub.app.sms.SmsMessageSource(getApplication()) }

    /**
     * Ob für diese Nachricht gerade geantwortet werden kann. Für Connectoren genügt eine
     * bekannte Konversation; beim Notification-Weg muss zusätzlich eine gültige
     * RemoteInput-Action vorliegen (kann jederzeit ungültig werden).
     */
    fun canReply(message: MessageEntity): Boolean = when (message.sourceKey) {
        com.hub.app.connectors.matrix.MatrixConnector.SOURCE_KEY ->
            ServiceLocator.matrixConnector(getApplication()).isConfigured() && message.conversationId != null
        com.hub.app.connectors.telegram.TelegramBotConnector.SOURCE_KEY ->
            ServiceLocator.telegramConnector(getApplication()).isConfigured() && message.conversationId != null
        com.hub.app.sms.SmsMessageSource.SOURCE_KEY -> smsSource.hasSendPermission()
        else -> message.hasQuickReply && notificationSource.canReplyTo(message.id)
    }

    fun sendReply(message: MessageEntity, text: String) = viewModelScope.launch {
        _quickReplyState.value = QuickReplyState.Sending
        val result = replySourceFor(message).sendReply(
            ReplyTarget(messageId = message.id, conversationId = message.conversationId),
            text
        )
        _quickReplyState.value = result.fold(
            onSuccess = {
                // Wer antwortet, hat gelesen.
                repository.markRead(message.id)
                QuickReplyState.Sent
            },
            onFailure = { error ->
                QuickReplyState.Failed(
                    error.message ?: "Antwort konnte nicht zugestellt werden."
                )
            }
        )
    }

    fun resetQuickReplyState() { _quickReplyState.value = QuickReplyState.Idle }

    /**
     * Öffnet die Nachricht in der Quell-App: bevorzugt über den gemerkten contentIntent
     * (springt direkt in den Chat), sonst als Rückfall über den Launch-Intent der App
     * (öffnet nur die App). Markiert die Nachricht dabei als gelesen.
     */
    fun openMessage(message: MessageEntity) {
        val context = getApplication<Application>()

        val pendingIntent = ContentIntentRegistry.get(message.id)
        // Ab Android 12 (S) blockiert das System "Notification-Trampolines": Feuert eine
        // Fremd-App den contentIntent nicht selbst, sondern wir, darf ein Umweg über
        // Broadcast/Service KEINE Activity mehr starten - der Tipp bliebe wirkungslos.
        // Deshalb den contentIntent nur nutzen, wenn er direkt eine Activity öffnet;
        // sonst gleich die App per Launch-Intent starten.
        val usable = pendingIntent != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || pendingIntent.isActivity)

        val opened = usable && runCatching { pendingIntent!!.send() }.isSuccess

        if (!opened) {
            message.sourcePackageName
                ?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
        }
        markRead(message.id)
    }

    fun markRead(id: String) = viewModelScope.launch { repository.markRead(id) }
    fun archive(id: String) = viewModelScope.launch { repository.archive(id) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    private val soundSettings by lazy {
        com.hub.app.notification.SoundSettings(getApplication())
    }

    /** Aktuell für diesen Absender hinterlegter Ton (URI-String) oder null. */
    fun currentSenderSound(message: MessageEntity): String? =
        soundSettings.soundFor(message.sourceKey, message.sender)

    /** Setzt (oder entfernt bei null) einen eigenen Ton für diesen Absender. */
    fun setSenderSound(message: MessageEntity, soundUri: String?) {
        soundSettings.setSenderSound(message.sourceKey, message.sender, soundUri)
    }
    fun unarchive(id: String) = viewModelScope.launch { repository.unarchive(id) }
    fun setPriority(id: String, priority: Boolean) = viewModelScope.launch { repository.setPriority(id, priority) }
}
