package com.hub.app.snooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hub.app.di.ServiceLocator
import com.hub.app.widget.HubWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hebt fällige Snoozes auf (Nachrichten erscheinen wieder im Feed) und plant den nächsten
 * Alarm für den nächstfälligen Snooze.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE_DUE) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.messageRepository(appContext)
                repo.clearExpiredSnoozes(System.currentTimeMillis())
                HubWidgetProvider.requestUpdate(appContext)
                // Nächsten noch offenen Snooze einplanen.
                repo.nextSnoozeDue()?.let { SnoozeScheduler.scheduleAt(appContext, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_DUE = "com.hub.app.action.SNOOZE_DUE"
    }
}
