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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Aktive Feed-Ansicht. POSTEINGANG zeigt nur ungelesene, nicht archivierte Nachrichten
 * (gelesene verschwinden); die Quellen-Auswahl (Drawer) läuft separat über
 * [HubViewModel.sourceFilter] und zeigt dann alle Nachrichten der gewählten App.
 */
enum class HubTab { POSTEINGANG, PRIORITAET, ARCHIV }

data class HubUiState(
    val messages: List<MessageEntity> = emptyList(),
    val sources: List<SourceAppEntity> = emptyList(),
    /** sourceKey -> Anzahl aktiver Nachrichten, für die Badges im Drawer. */
    val sourceCounts: Map<String, Int> = emptyMap(),
    val tab: HubTab = HubTab.POSTEINGANG,
    val sourceFilter: String? = null,
    val hasNotificationAccess: Boolean = false
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val notificationSource = NotificationMessageSource(application)

    private val _tab = MutableStateFlow(HubTab.POSTEINGANG)
    val tab: StateFlow<HubTab> = _tab.asStateFlow()

    private val _quickReplyState = MutableStateFlow<QuickReplyState>(QuickReplyState.Idle)
    val quickReplyState: StateFlow<QuickReplyState> = _quickReplyState.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)
    private val _hasNotificationAccess = MutableStateFlow(NotificationAccess.isGranted(application))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = combine(_tab, _sourceFilter) { tab, source -> tab to source }
        .flatMapLatest { (tab, source) ->
            when {
                // Ein aktiver Quellenfilter (Drawer-Auswahl) gewinnt immer gegenüber dem Tab
                // und zeigt ALLE Nachrichten der App.
                source != null -> repository.observeBySource(source)
                tab == HubTab.PRIORITAET -> repository.observePriorityHub()
                tab == HubTab.ARCHIV -> repository.observeArchived()
                else -> repository.observeInbox()
            }
        }

    private val sourceCounts = repository.observeSourceCounts()

    val uiState: StateFlow<HubUiState> = combine(
        messages,
        repository.observeSources(),
        sourceCounts,
        _tab,
        combine(_sourceFilter, _hasNotificationAccess) { filter, access -> filter to access }
    ) { messages, sources, counts, tab, (sourceFilter, access) ->
        HubUiState(
            messages = messages,
            sources = sources,
            sourceCounts = counts.associate { it.sourceKey to it.count },
            tab = tab,
            sourceFilter = sourceFilter,
            hasNotificationAccess = access
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    fun selectTab(tab: HubTab) { _tab.value = tab }

    fun selectSourceFilter(sourceKey: String?) { _sourceFilter.value = sourceKey }

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
     * Ob für diese Nachricht gerade geantwortet werden kann. Wird pro Peek-Öffnung
     * abgefragt, weil die zugrundeliegende PendingIntent jederzeit ungültig werden kann.
     */
    fun canQuickReply(message: MessageEntity): Boolean =
        message.hasQuickReply && notificationSource.canReplyTo(message.id)

    fun sendQuickReply(message: MessageEntity, text: String) = viewModelScope.launch {
        _quickReplyState.value = QuickReplyState.Sending
        val result = notificationSource.sendReply(
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
    fun unarchive(id: String) = viewModelScope.launch { repository.unarchive(id) }
    fun setPriority(id: String, priority: Boolean) = viewModelScope.launch { repository.setPriority(id, priority) }
}
