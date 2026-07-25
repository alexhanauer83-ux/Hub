package com.hub.app.notification

import android.content.Context

/**
 * Einfache, nicht sensible App-Einstellungen rund um Benachrichtigungen (normale
 * SharedPreferences genügen – hier liegen keine Geheimnisse).
 */
class NotificationSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Wenn aktiv, entfernt Hub eingehende Fremd-Benachrichtigungen aus der Statusleiste
     * und postet stattdessen eine eigene – oben erscheint dann nur noch Hub.
     */
    var replaceOtherNotifications: Boolean
        get() = prefs.getBoolean(KEY_REPLACE, false)
        set(value) = prefs.edit().putBoolean(KEY_REPLACE, value).apply()

    /**
     * Quellen (sourceKeys), für die Hub keine eigene Benachrichtigung/keinen Ton erzeugt.
     * Die Nachrichten landen weiterhin im Feed – nur die Alarmierung entfällt.
     */
    fun mutedSources(): Set<String> =
        prefs.getStringSet(KEY_MUTED, emptySet())?.toSet() ?: emptySet()

    fun isMuted(sourceKey: String): Boolean = sourceKey in mutedSources()

    fun setMuted(sourceKey: String, muted: Boolean) {
        val updated = mutedSources().toMutableSet().apply {
            if (muted) add(sourceKey) else remove(sourceKey)
        }
        prefs.edit().putStringSet(KEY_MUTED, updated).apply()
    }

    private companion object {
        const val PREFS_NAME = "hub_notification_settings"
        const val KEY_REPLACE = "replace_other_notifications"
        const val KEY_MUTED = "muted_sources"
    }
}
