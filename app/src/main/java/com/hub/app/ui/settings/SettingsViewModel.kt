package com.hub.app.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import com.hub.app.notification.NotificationAccess
import com.hub.app.notification.NotificationSettings
import com.hub.app.security.AppLockManager
import com.hub.app.sms.SmsDefaultAppManager
import com.hub.app.sms.SmsMessageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Downloading : UpdateState
    data class Available(val info: com.hub.app.update.UpdateManager.UpdateInfo) : UpdateState
    /** Download fertig, aber Hub darf noch keine Apps installieren. */
    data class NeedsInstallPermission(val info: com.hub.app.update.UpdateManager.UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class SettingsUiState(
    val sources: List<SourceAppEntity> = emptyList(),
    val hasNotificationAccess: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val hasSmsReadPermission: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val canUseAppLock: Boolean = false,
    val replaceOtherNotifications: Boolean = false,
    val mutedSources: Set<String> = emptySet(),
    val backgroundSyncEnabled: Boolean = true,
    val hasConnector: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22 * 60,
    val quietHoursEnd: Int = 7 * 60,
    val retentionDays: Int = 7,
    val rightSwipe: com.hub.app.notification.SwipeAction = com.hub.app.notification.SwipeAction.READ,
    val leftSwipe: com.hub.app.notification.SwipeAction = com.hub.app.notification.SwipeAction.ARCHIVE
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = ServiceLocator.messageRepository(application)
    private val smsManager = SmsDefaultAppManager(application)
    private val smsSource = SmsMessageSource(application)
    private val appLock = AppLockManager(application)
    private val notificationSettings = NotificationSettings(application)
    private val soundSettings = com.hub.app.notification.SoundSettings(application)

    private val systemState = MutableStateFlow(readSystemState())

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.observeSources(), systemState) { sources, system ->
            system.copy(sources = sources)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // --- Selbst-Update ---
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    val currentVersion: String = com.hub.app.update.UpdateManager.currentVersion(application)

    fun checkForUpdate() = viewModelScope.launch {
        _updateState.value = UpdateState.Checking
        com.hub.app.update.UpdateManager.check(getApplication()).fold(
            onSuccess = { info ->
                _updateState.value =
                    if (info == null) UpdateState.UpToDate
                    else UpdateState.Available(info)
            },
            onFailure = { _updateState.value = UpdateState.Error(it.message ?: "Prüfung fehlgeschlagen") }
        )
    }

    private var pendingInfo: com.hub.app.update.UpdateManager.UpdateInfo? = null

    fun downloadAndInstall(info: com.hub.app.update.UpdateManager.UpdateInfo) {
        val app = getApplication<Application>()
        pendingInfo = info
        // Erst Berechtigung sicherstellen, dann herunterladen (sonst laedt es, laesst sich
        // aber nicht installieren).
        if (!com.hub.app.update.UpdateManager.canInstall(app)) {
            _updateState.value = UpdateState.NeedsInstallPermission(info)
            return
        }
        // Download laeuft ueber den System-DownloadManager im Hintergrund weiter (ueberlebt
        // Bildschirmsperre/App-Wechsel); Installation startet der UpdateDownloadReceiver.
        com.hub.app.update.UpdateManager.enqueueDownload(app, info)
        _updateState.value = UpdateState.Downloading
    }

    /** Nachdem der Nutzer die Installations-Berechtigung erteilt hat: Download erneut anstossen. */
    fun installPending() {
        val info = pendingInfo ?: return
        downloadAndInstall(info)
    }

    fun installPermissionIntent(): Intent =
        com.hub.app.update.UpdateManager.installPermissionIntent(getApplication())

    /** Nach Rückkehr aus Systemeinstellungen/Rollen-Dialog neu einlesen. */
    fun refreshSystemState() {
        systemState.value = readSystemState()
    }

    private fun readSystemState() = SettingsUiState(
        hasNotificationAccess = NotificationAccess.isGranted(getApplication()),
        isDefaultSmsApp = smsManager.isDefaultSmsApp(),
        hasSmsReadPermission = smsSource.hasReadPermission(),
        isAppLockEnabled = appLock.isLockEnabled,
        canUseAppLock = appLock.canAuthenticate(),
        replaceOtherNotifications = notificationSettings.replaceOtherNotifications,
        mutedSources = notificationSettings.mutedSources(),
        backgroundSyncEnabled = notificationSettings.backgroundSyncEnabled,
        hasConnector = com.hub.app.connectors.ConnectorSyncService.anyConnectorConfigured(getApplication()),
        quietHoursEnabled = notificationSettings.quietHoursEnabled,
        quietHoursStart = notificationSettings.quietHoursStartMinutes,
        quietHoursEnd = notificationSettings.quietHoursEndMinutes,
        retentionDays = notificationSettings.retentionDays,
        rightSwipe = notificationSettings.rightSwipeAction,
        leftSwipe = notificationSettings.leftSwipeAction
    )

    fun setRightSwipe(action: com.hub.app.notification.SwipeAction) {
        notificationSettings.rightSwipeAction = action
        refreshSystemState()
    }

    fun setLeftSwipe(action: com.hub.app.notification.SwipeAction) {
        notificationSettings.leftSwipeAction = action
        refreshSystemState()
    }

    /** Leert den gesamten Nachrichten-Cache (behebt „alte Nachrichten bleiben stehen"). */
    fun clearAllMessages() = viewModelScope.launch { repository.clearAllMessages() }

    /** Setzt die Aufbewahrungsdauer und räumt sofort entsprechend auf. */
    fun setRetentionDays(days: Int) {
        notificationSettings.retentionDays = days
        refreshSystemState()
        viewModelScope.launch { repository.pruneRead(days) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        notificationSettings.quietHoursEnabled = enabled
        refreshSystemState()
    }

    fun setQuietHoursStart(minutes: Int) {
        notificationSettings.quietHoursStartMinutes = minutes
        refreshSystemState()
    }

    fun setQuietHoursEnd(minutes: Int) {
        notificationSettings.quietHoursEndMinutes = minutes
        refreshSystemState()
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        notificationSettings.backgroundSyncEnabled = enabled
        val app = getApplication<Application>()
        if (enabled) com.hub.app.connectors.ConnectorSyncService.startIfEnabled(app)
        else com.hub.app.connectors.ConnectorSyncService.stop(app)
        refreshSystemState()
    }

    fun setSourceMuted(sourceKey: String, muted: Boolean) {
        notificationSettings.setMuted(sourceKey, muted)
        refreshSystemState()
    }

    /** Aktuell für diese Quelle hinterlegter Ton (URI-String) oder null (=Standard). */
    fun currentSourceSound(sourceKey: String): String? = soundSettings.sourceSound(sourceKey)

    /** Setzt (oder entfernt bei null) einen eigenen Benachrichtigungston für die ganze Quelle. */
    fun setSourceSound(sourceKey: String, soundUri: String?) {
        soundSettings.setSourceSound(sourceKey, soundUri)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        appLock.isLockEnabled = enabled
        refreshSystemState()
    }

    fun setReplaceOtherNotifications(enabled: Boolean) {
        notificationSettings.replaceOtherNotifications = enabled
        refreshSystemState()
    }

    fun smsRoleRequestIntent() = smsManager.requestRoleIntent()

    fun smsRoleSettingsIntent() = smsManager.releaseRoleSettingsIntent()

    fun setSourceEnabled(sourceKey: String, enabled: Boolean) = viewModelScope.launch {
        repository.setSourceEnabled(sourceKey, enabled)
    }

    fun setSourcePriority(sourceKey: String, isPriority: Boolean) = viewModelScope.launch {
        repository.setSourcePriority(sourceKey, isPriority)
    }

    /**
     * Importiert den bestehenden SMS-Verlauf aus dem ContentProvider – der eigentliche
     * Mehrwert gegenüber dem Notification-Abgriff, der nur neue Nachrichten sieht.
     */
    fun importSmsHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.registerSource(
                SourceAppEntity(
                    sourceKey = SmsMessageSource.SOURCE_KEY,
                    label = "SMS",
                    packageName = null,
                    enabled = true,
                    isNativeConnector = true
                )
            )
            smsSource.importInbox(repository)
        }
    }
}
