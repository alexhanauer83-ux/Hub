package com.hub.app.notification

import android.content.Context
import java.util.Calendar

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

    /** Hintergrund-Empfang der API-Connectoren (Foreground-Service). Standard: an. */
    var backgroundSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_SYNC, value).apply()

    /**
     * Aufbewahrung **gelesener** Nachrichten in Tagen – ältere werden automatisch gelöscht,
     * damit der Feed nicht unbegrenzt wächst. Ungelesene bleiben immer. 0 = unbegrenzt (aus).
     * Standard: 7 Tage.
     */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    /**
     * Ob der einmalige Gesten-Hinweis im Posteingang bereits weggetippt wurde. Startet auf
     * false, damit neue Nutzer die Wisch-/Tipp-Gesten überhaupt entdecken.
     */
    var gestureHintDismissed: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_HINT, value).apply()

    /**
     * Ruhezeiten: In diesem Zeitfenster postet Hub seine eigenen Benachrichtigungen still
     * (kein Ton/Heads-up); die Nachricht bleibt im Feed. Zeiten als Minuten seit Mitternacht.
     */
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    var quietHoursStartMinutes: Int
        get() = prefs.getInt(KEY_QUIET_START, 22 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_START, value).apply()

    var quietHoursEndMinutes: Int
        get() = prefs.getInt(KEY_QUIET_END, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_QUIET_END, value).apply()

    /** Liegt [nowMinutes] (Minuten seit Mitternacht) aktuell in der Ruhezeit? Behandelt Mitternachts-Überlauf. */
    fun isInQuietHours(nowMinutes: Int = nowMinutesOfDay()): Boolean {
        if (!quietHoursEnabled) return false
        val start = quietHoursStartMinutes
        val end = quietHoursEndMinutes
        return when {
            start == end -> false
            start < end -> nowMinutes in start until end
            else -> nowMinutes >= start || nowMinutes < end // über Mitternacht hinweg
        }
    }

    private fun nowMinutesOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Angepinnte Quellen-Reiter (erscheinen vorne in der Reiter-Leiste). */
    fun pinnedSources(): Set<String> = prefs.getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun setPinned(sourceKey: String, pinned: Boolean) {
        val updated = pinnedSources().toMutableSet().apply { if (pinned) add(sourceKey) else remove(sourceKey) }
        prefs.edit().putStringSet(KEY_PINNED, updated).apply()
    }

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

    /**
     * Einzelne stummgeschaltete Konversationen (Schlüssel = sourceKey + Gruppenwert). Betrifft
     * nur die Alarmierung (Hub-Benachrichtigung/Ton) – die Nachrichten bleiben im Feed.
     */
    fun mutedConversations(): Set<String> =
        prefs.getStringSet(KEY_MUTED_CONV, emptySet())?.toSet() ?: emptySet()

    fun isConversationMuted(sourceKey: String, groupValue: String): Boolean =
        conversationKey(sourceKey, groupValue) in mutedConversations()

    fun setConversationMuted(sourceKey: String, groupValue: String, muted: Boolean) {
        val key = conversationKey(sourceKey, groupValue)
        val updated = mutedConversations().toMutableSet().apply {
            if (muted) add(key) else remove(key)
        }
        prefs.edit().putStringSet(KEY_MUTED_CONV, updated).apply()
    }

    /** Angepinnte Konversationen (Schlüssel = sourceKey + Gruppenwert) – bleiben oben in der Liste. */
    fun pinnedConversations(): Set<String> =
        prefs.getStringSet(KEY_PINNED_CONV, emptySet())?.toSet() ?: emptySet()

    fun isConversationPinned(sourceKey: String, groupValue: String): Boolean =
        conversationKey(sourceKey, groupValue) in pinnedConversations()

    fun setConversationPinned(sourceKey: String, groupValue: String, pinned: Boolean) {
        val key = conversationKey(sourceKey, groupValue)
        val updated = pinnedConversations().toMutableSet().apply {
            if (pinned) add(key) else remove(key)
        }
        prefs.edit().putStringSet(KEY_PINNED_CONV, updated).apply()
    }

    // \u0001 als Trenner: kommt in sourceKey/Gruppenwert praktisch nicht vor.
    private fun conversationKey(sourceKey: String, groupValue: String) = "$sourceKey\u0001$groupValue"

    private companion object {
        const val PREFS_NAME = "hub_notification_settings"
        const val KEY_REPLACE = "replace_other_notifications"
        const val KEY_MUTED = "muted_sources"
        const val KEY_MUTED_CONV = "muted_conversations"
        const val KEY_PINNED_CONV = "pinned_conversations"
        const val KEY_BG_SYNC = "background_sync"
        const val KEY_PINNED = "pinned_sources"
        const val KEY_GESTURE_HINT = "gesture_hint_dismissed"
        const val KEY_RETENTION_DAYS = "retention_days"
        const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START = "quiet_hours_start"
        const val KEY_QUIET_END = "quiet_hours_end"
    }
}
