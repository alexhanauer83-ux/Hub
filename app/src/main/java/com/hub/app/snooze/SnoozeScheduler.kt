package com.hub.app.snooze

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Plant einen (ungenauen, doze-tauglichen) Alarm, der abgelaufene Snoozes aufhebt, damit
 * zurückgestellte Nachrichten von selbst wieder im Feed erscheinen. Ungenau reicht – auf
 * die Minute genau muss ein Snooze nicht sein, und so wird keine Exact-Alarm-Berechtigung
 * benötigt.
 */
object SnoozeScheduler {

    fun scheduleAt(context: Context, atMillis: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        // setAndAllowWhileIdle feuert auch im Doze-Modus, ist aber bewusst ungenau.
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE_DUE
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
