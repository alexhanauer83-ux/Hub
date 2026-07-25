package com.hub.app.ui.settings

import android.app.Application
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

data class SettingsUiState(
    val sources: List<SourceAppEntity> = emptyList(),
    val hasNotificationAccess: Boolean = false,
    val isDefaultSmsApp: Boolean = false,
    val hasSmsReadPermission: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val canUseAppLock: Boolean = false,
    val replaceOtherNotifications: Boolean = false
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
        replaceOtherNotifications = notificationSettings.replaceOtherNotifications
    )

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
