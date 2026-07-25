package com.hub.app.notification

import android.app.PendingIntent

/**
 * Hält die `contentIntent`-PendingIntents lebender Benachrichtigungen. Das ist genau der
 * Intent, den Android feuert, wenn man eine Benachrichtigung antippt – er öffnet die
 * Quell-App an der passenden Stelle (in der Regel direkt im betreffenden Chat).
 *
 * Wie [QuickReplyRegistry] rein in-memory: Eine fremde [PendingIntent] ist nur gültig,
 * solange die zugehörige Notification existiert, und lässt sich nicht persistieren. Nach
 * Verwerfen der Notification oder einem Geräteneustart greift in der UI der Rückfall
 * (App per Launch-Intent öffnen, ohne direkt zum Chat zu springen).
 */
object ContentIntentRegistry {

    private data class Entry(val pendingIntent: PendingIntent, val notificationKey: String)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun register(messageId: String, notificationKey: String, pendingIntent: PendingIntent) {
        entries[messageId] = Entry(pendingIntent, notificationKey)
    }

    @Synchronized
    fun get(messageId: String): PendingIntent? = entries[messageId]?.pendingIntent

    @Synchronized
    fun removeByNotificationKey(notificationKey: String) {
        entries.entries.removeAll { it.value.notificationKey == notificationKey }
    }

    @Synchronized
    fun clear() = entries.clear()
}
