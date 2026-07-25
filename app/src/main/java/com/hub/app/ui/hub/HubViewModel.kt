package com.hub.app.ui.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import com.hub.app.notification.NotificationAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Aktive Feed-Ansicht. Filter nach Quelle wird separat über [HubViewModel.sourceFilter] gesetzt. */
enum class HubTab { ALLE, UNGELESEN, PRIORITAET, ARCHIV }

data class HubUiState(
    val messages: List<MessageEntity> = emptyList(),
    val sources: List<SourceAppEntity> = emptyList(),
    val tab: HubTab = HubTab.ALLE,
    val sourceFilter: String? = null,
    val hasNotificationAccess: Boolean = false
)

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val _tab = MutableStateFlow(HubTab.ALLE)
    val tab: StateFlow<HubTab> = _tab.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)
    private val _hasNotificationAccess = MutableStateFlow(NotificationAccess.isGranted(application))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = combine(_tab, _sourceFilter) { tab, source -> tab to source }
        .flatMapLatest { (tab, source) ->
            when {
                // Ein aktiver Quellenfilter gewinnt gegenüber dem Tab, außer im Archiv.
                source != null && tab != HubTab.ARCHIV -> repository.observeBySource(source)
                tab == HubTab.UNGELESEN -> repository.observeUnread()
                tab == HubTab.PRIORITAET -> repository.observePriorityHub()
                tab == HubTab.ARCHIV -> repository.observeArchived()
                else -> repository.observeInbox()
            }
        }

    val uiState: StateFlow<HubUiState> = combine(
        messages,
        repository.observeSources(),
        _tab,
        _sourceFilter,
        _hasNotificationAccess
    ) { messages, sources, tab, sourceFilter, access ->
        HubUiState(
            messages = messages,
            sources = sources,
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

    fun markRead(id: String) = viewModelScope.launch { repository.markRead(id) }
    fun archive(id: String) = viewModelScope.launch { repository.archive(id) }
    fun unarchive(id: String) = viewModelScope.launch { repository.unarchive(id) }
    fun setPriority(id: String, priority: Boolean) = viewModelScope.launch { repository.setPriority(id, priority) }
}
