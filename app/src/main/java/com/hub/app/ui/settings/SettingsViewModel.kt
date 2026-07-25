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
    val mutedSources: Set<String> = emptySet()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository = ServiceLocator.messageRepository(application)
    private val smsManager = SmsDefaultAppManager(application)
    private val smsSource = SmsMessageSource(application)
    private val appLock = AppLockManager(application)
    private val notificationSettings = NotificationSettings(application)

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

    private var pendingApk: java.io.File? = null

    fun downloadAndInstall(info: com.hub.app.update.UpdateManager.UpdateInfo) = viewModelScope.launch {
        val app = getApplication<Application>()
        _updateState.value = UpdateState.Downloading
        com.hub.app.update.UpdateManager.download(app, info).fold(
            onSuccess = { file ->
                pendingApk = file
                if (com.hub.app.update.UpdateManager.canInstall(app)) {
                    _updateState.value = UpdateState.Available(info)
                    com.hub.app.update.UpdateManager.install(app, file)
                } else {
                    // Hub darf noch keine Apps installieren -> Nutzer muss das erst erlauben.
                    _updateState.value = UpdateState.NeedsInstallPermission(info)
                }
            },
            onFailure = { _updateState.value = UpdateState.Error(it.message ?: "Download fehlgeschlagen") }
        )
    }

    /** Nachdem der Nutzer die Installations-Berechtigung erteilt hat: erneut installieren. */
    fun installPending() {
        val app = getApplication<Application>()
        val file = pendingApk ?: return
        if (com.hub.app.update.UpdateManager.canInstall(app)) {
            com.hub.app.update.UpdateManager.install(app, file)
        }
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
        mutedSources = notificationSettings.mutedSources()
    )

    fun setSourceMuted(sourceKey: String, muted: Boolean) {
        notificationSettings.setMuted(sourceKey, muted)
        refreshSystemState()
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
