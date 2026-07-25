package com.hub.app.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Notification-Access ist eine "Special Access"-Berechtigung: sie lässt sich **nicht**
 * per `ActivityCompat.requestPermissions` anfordern. Der Nutzer muss sie in den
 * Systemeinstellungen erteilen. Wir prüfen sie über die Secure-Setting-Liste
 * `enabled_notification_listeners`, in der jede freigegebene Listener-Komponente steht.
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val ourComponent = ComponentName(context, HubNotificationListenerService::class.java)
        return enabled.split(":")
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == ourComponent }
    }

    /**
     * Öffnet die Systemeinstellungen. Erst ab Android 11 (API 30) gibt es den Intent, der
     * direkt zur eigenen App springt; darunter (unser minSdk 29) landet man in der
     * allgemeinen Liste und muss "Hub" selbst auswählen.
     */
    fun settingsIntent(context: Context): Intent =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, HubNotificationListenerService::class.java).flattenToString()
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
