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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Aktive Feed-Ansicht. POSTEINGANG zeigt nur ungelesene, nicht archivierte Nachrichten
 * (gelesene verschwinden); die Quellen-Auswahl (Drawer) läuft separat über
 * [HubViewModel.sourceFilter] und zeigt dann alle Nachrichten der gewählten App.
 */
enum class HubTab { POSTEINGANG, PRIORITAET, ARCHIV }

/** Zustand der Mehrfachauswahl. */
data class SelectionState(val active: Boolean = false, val ids: Set<String> = emptySet())

/**
 * Eine widerrufbare Aktion: [label] beschreibt sie in der Snackbar, [undo] macht sie
 * rückgängig, wenn der Nutzer „Rückgängig" antippt.
 */
data class UndoRequest(val label: String, val undo: () -> Unit)

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
    val hasImage: Boolean,
    val pinned: Boolean = false
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
    val pinnedSources: Set<String> = emptySet(),
    val hasNotificationAccess: Boolean = false,
    val gestureHintVisible: Boolean = false
) {
    val isSearching: Boolean get() = searchQuery != null

    /**
     * Gruppierte Übersicht anzeigen? Im Posteingang sowie in jeder Quellen-Auswahl
     * (Matrix/Telegram/E-Mail-Reiter) – nicht in Suche, Konversations-Detail, Priorität/Archiv.
     */
    val showConversations: Boolean
        get() = grouped && !isSearching && conversationFilter == null &&
            (sourceFilter != null || tab == HubTab.POSTEINGANG)
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
    private val notificationSettings = com.hub.app.notification.NotificationSettings(application)
    private val _pinnedSources = MutableStateFlow(notificationSettings.pinnedSources())
    private val _hasNotificationAccess = MutableStateFlow(NotificationAccess.isGranted(application))
    private val _gestureHintVisible = MutableStateFlow(!notificationSettings.gestureHintDismissed)

    /** Widerrufbare Aktionen für die Snackbar (Archivieren/Löschen/Gelesen …). */
    private val _undo = MutableSharedFlow<UndoRequest>(extraBufferCapacity = 4)
    val undoEvents: SharedFlow<UndoRequest> = _undo.asSharedFlow()

    /** Blendet den einmaligen Gesten-Hinweis dauerhaft aus. */
    fun dismissGestureHint() {
        notificationSettings.gestureHintDismissed = true
        _gestureHintVisible.value = false
    }

    fun togglePinnedSource(sourceKey: String) {
        val pinned = sourceKey !in _pinnedSources.value
        notificationSettings.setPinned(sourceKey, pinned)
        _pinnedSources.value = notificationSettings.pinnedSources()
    }

    /** Ob die Konversation dieser Nachricht aktuell stummgeschaltet ist (keine Alarmierung). */
    fun isConversationMuted(message: MessageEntity): Boolean =
        notificationSettings.isConversationMuted(message.sourceKey, message.groupValue())

    /** Schaltet die Konversation dieser Nachricht stumm bzw. hebt die Stummschaltung auf. */
    fun toggleConversationMuted(message: MessageEntity) {
        val muted = !isConversationMuted(message)
        notificationSettings.setConversationMuted(message.sourceKey, message.groupValue(), muted)
    }

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

    // Gruppierte Übersicht – abhängig vom Filter: Posteingang ODER die gewählte Quelle
    // (Matrix/Telegram/E-Mail-Reiter) zu Unterhaltungen zusammengefasst.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversationsRaw = filter.flatMapLatest { f ->
        when {
            !f.searchQuery.isNullOrBlank() || f.conversationFilter != null -> flowOf(emptyList())
            f.sourceFilter != null -> repository.observeBySource(f.sourceFilter).map { groupIntoConversations(it) }
            f.tab == HubTab.POSTEINGANG -> repository.observeInbox().map { groupIntoConversations(it) }
            else -> flowOf(emptyList())
        }
    }

    private val _pinnedConversations = MutableStateFlow(notificationSettings.pinnedConversations())

    // Angepinnte Konversationen nach oben, der Rest nach Zeit – reagiert live auf Pin-Änderungen.
    private val conversations = combine(conversationsRaw, _pinnedConversations) { convos, _ ->
        convos
            .map { it.copy(pinned = notificationSettings.isConversationPinned(it.sourceKey, it.groupValue)) }
            .sortedWith(
                compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.latestTimestamp }
            )
    }

    /** Pinnt eine Konversation an die Spitze bzw. löst sie wieder (wirkt in der Sortierung oben). */
    fun toggleConversationPin(sourceKey: String, groupValue: String) {
        val pin = !notificationSettings.isConversationPinned(sourceKey, groupValue)
        notificationSettings.setConversationPinned(sourceKey, groupValue, pin)
        _pinnedConversations.value = notificationSettings.pinnedConversations()
    }

    private val sourceCounts = repository.observeSourceCounts()

    /** Nebenzustände, die nicht vom [FeedFilter] abhängen, gebündelt (combine bleibt 5-armig). */
    private data class Extras(
        val counts: List<com.hub.app.data.local.dao.SourceCount>,
        val access: Boolean,
        val pinned: Set<String>,
        val gestureHint: Boolean
    )

    private val extras = combine(
        sourceCounts, _hasNotificationAccess, _pinnedSources, _gestureHintVisible
    ) { counts, access, pinned, gestureHint ->
        Extras(counts, access, pinned, gestureHint)
    }

    val uiState: StateFlow<HubUiState> = combine(
        messages,
        conversations,
        repository.observeSources(),
        extras,
        filter
    ) { messages, conversations, sources, extra, f ->
        HubUiState(
            messages = messages,
            conversations = conversations,
            sources = sources,
            sourceCounts = extra.counts.associate { it.sourceKey to it.count },
            tab = f.tab,
            sourceFilter = f.sourceFilter,
            conversationFilter = f.conversationFilter,
            grouped = f.grouped,
            searchQuery = f.searchQuery,
            pinnedSources = extra.pinned,
            hasNotificationAccess = extra.access,
            gestureHintVisible = extra.gestureHint
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    private fun groupIntoConversations(messages: List<MessageEntity>): List<ConversationSummary> =
        messages.groupBy { it.sourceKey to it.groupValue() }
            .map { (key, msgs) ->
                // observeInbox liefert bereits nach Zeit absteigend -> erstes = neuestes.
                val latest = msgs.first()
                // Titel: expliziter Konversationstitel (z. B. Matrix-Raumname) > 1:1-Absender
                // (nur ein Absender) > Gruppenschlüssel.
                val title = latest.conversationTitle?.takeIf { it.isNotBlank() }
                    ?: if (msgs.all { it.sender == latest.sender }) latest.sender else latest.groupValue()
                ConversationSummary(
                    sourceKey = key.first,
                    groupValue = key.second,
                    title = title,
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
        _sourceFilter.value = null
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
                // Wer antwortet, hat gelesen. Antwort im Chatverlauf ablegen.
                repository.markRead(message.id)
                repository.recordOutgoing(message, text)
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

    /** Ob für diese Nachricht eine Sprachnachricht gesendet werden kann (aktuell nur Matrix). */
    fun canSendVoice(message: MessageEntity): Boolean =
        message.sourceKey == com.hub.app.connectors.matrix.MatrixConnector.SOURCE_KEY &&
            message.conversationId != null

    /** Nimmt eine aufgenommene Sprachnachricht und sendet sie in den Matrix-Raum. */
    fun sendVoice(message: MessageEntity, file: java.io.File) = viewModelScope.launch {
        _quickReplyState.value = QuickReplyState.Sending
        val roomId = message.conversationId ?: ""
        val result = ServiceLocator.matrixConnector(getApplication()).sendVoice(roomId, file)
        _quickReplyState.value = result.fold(
            onSuccess = {
                repository.markRead(message.id)
                repository.recordOutgoing(message, "🎤 Sprachnachricht")
                QuickReplyState.Sent
            },
            onFailure = { QuickReplyState.Failed(it.message ?: "Sprachnachricht fehlgeschlagen") }
        )
        runCatching { file.delete() }
    }

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

    /** Ohne Snackbar – für interne Aufrufe (Antwort senden, App öffnen). */
    fun markRead(id: String) = viewModelScope.launch { repository.markRead(id) }

    /** Aus Wisch-Geste/Peek: als gelesen markieren, aber mit „Rückgängig". */
    fun markReadUndoable(id: String) = viewModelScope.launch {
        repository.markRead(id)
        _undo.tryEmit(UndoRequest("Als gelesen markiert") {
            viewModelScope.launch { repository.markUnread(id) }
        })
    }

    /** Setzt eine gelesene Nachricht wieder auf ungelesen (Peek-Aktion). */
    fun markUnread(id: String) = viewModelScope.launch { repository.markUnread(id) }

    fun archive(id: String) = viewModelScope.launch {
        repository.archive(id)
        _undo.tryEmit(UndoRequest("Archiviert") { unarchive(id) })
    }

    fun delete(id: String) = viewModelScope.launch {
        val entity = repository.getById(id)
        repository.delete(id)
        if (entity != null) {
            _undo.tryEmit(UndoRequest("Gelöscht") {
                viewModelScope.launch { repository.restore(listOf(entity)) }
            })
        }
    }

    // --- Sammelaktionen ---
    fun markAllRead() = viewModelScope.launch {
        val ids = repository.unreadInboxIds()
        repository.markAllRead()
        if (ids.isNotEmpty()) {
            _undo.tryEmit(UndoRequest("Alle als gelesen markiert") {
                viewModelScope.launch { repository.markUnreadIn(ids) }
            })
        }
    }

    /**
     * Markiert alle ungelesenen Nachrichten der **aktuell geöffneten Ansicht** als gelesen –
     * funktioniert in jeder Kategorie (Posteingang, Quelle/Reiter, Priorität, Konversation),
     * weil [HubUiState.messages] genau die gefilterte Liste der aktiven Ansicht enthält.
     */
    fun markVisibleRead() = viewModelScope.launch {
        val ids = uiState.value.messages.filter { !it.isRead }.map { it.id }
        if (ids.isEmpty()) return@launch
        repository.markReadIn(ids)
        _undo.tryEmit(UndoRequest("${ids.size} als gelesen markiert") {
            viewModelScope.launch { repository.markUnreadIn(ids) }
        })
    }

    // --- Snooze ---
    /** Stellt eine Nachricht für [durationMillis] zurück und plant das Wiederauftauchen. */
    fun snooze(message: MessageEntity, durationMillis: Long) = viewModelScope.launch {
        val until = System.currentTimeMillis() + durationMillis
        repository.snooze(message.id, until)
        com.hub.app.snooze.SnoozeScheduler.scheduleAt(getApplication(), until)
    }

    // --- Mehrfachauswahl ---
    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectionState: StateFlow<SelectionState> =
        combine(_selectionMode, _selectedIds) { active, ids -> SelectionState(active, ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectionState())

    fun enterSelection(message: MessageEntity) {
        _selectionMode.value = true
        _selectedIds.value = setOf(message.id)
    }

    fun toggleSelected(id: String) {
        val next = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
        _selectedIds.value = next
        if (next.isEmpty()) _selectionMode.value = false
    }

    fun exitSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun markSelectedRead() = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        repository.markReadIn(ids); exitSelection()
        if (ids.isNotEmpty()) {
            _undo.tryEmit(UndoRequest("${ids.size} als gelesen markiert") {
                viewModelScope.launch { repository.markUnreadIn(ids) }
            })
        }
    }

    fun archiveSelected() = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        repository.archiveIn(ids); exitSelection()
        if (ids.isNotEmpty()) {
            _undo.tryEmit(UndoRequest("${ids.size} archiviert") {
                viewModelScope.launch { repository.unarchiveIn(ids) }
            })
        }
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selectedIds.value.toList()
        val entities = repository.getByIds(ids)
        repository.deleteIn(ids); exitSelection()
        if (entities.isNotEmpty()) {
            _undo.tryEmit(UndoRequest("${entities.size} gelöscht") {
                viewModelScope.launch { repository.restore(entities) }
            })
        }
    }

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
