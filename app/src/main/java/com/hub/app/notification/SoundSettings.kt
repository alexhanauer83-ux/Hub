package com.hub.app.notification

import android.content.Context

/**
 * Merkt sich pro Absender (und optional pro Quelle) einen eigenen Benachrichtigungston.
 * Greift für Hubs *eigene* Benachrichtigungen im Ersetzen-Modus (siehe [HubNotifier]).
 *
 * Werte sind Ringtone-URIs (als String); `null`/leer bedeutet Standardton der App.
 */
class SoundSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ton für einen konkreten Absender innerhalb einer Quelle. */
    fun setSenderSound(sourceKey: String, sender: String, soundUri: String?) {
        prefs.edit().apply {
            val key = senderKey(sourceKey, sender)
            if (soundUri.isNullOrBlank()) remove(key) else putString(key, soundUri)
        }.apply()
    }

    fun senderSound(sourceKey: String, sender: String): String? =
        prefs.getString(senderKey(sourceKey, sender), null)

    /** Ton für eine ganze Quelle (Fallback, wenn kein absenderspezifischer Ton gesetzt ist). */
    fun setSourceSound(sourceKey: String, soundUri: String?) {
        prefs.edit().apply {
            val key = sourceKey(sourceKey)
            if (soundUri.isNullOrBlank()) remove(key) else putString(key, soundUri)
        }.apply()
    }

    /** Nur der quellenweite Ton (ohne Absender-Fallback) – für die Einstellungen. */
    fun sourceSound(sourceKey: String): String? = prefs.getString(sourceKey(sourceKey), null)

    /** Effektiver Ton: erst Absender, dann Quelle, sonst null (=Standard). */
    fun soundFor(sourceKey: String, sender: String): String? =
        senderSound(sourceKey, sender) ?: prefs.getString(sourceKey(sourceKey), null)

    private fun senderKey(sourceKey: String, sender: String) = "sender:$sourceKey:$sender"
    private fun sourceKey(sourceKey: String) = "source:$sourceKey"

    private companion object {
        const val PREFS_NAME = "hub_sound_settings"
    }
}
