package com.hub.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hub.app.MainActivity
import com.hub.app.R
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Homescreen-Widget: zeigt die neuesten Hub-Nachrichten + Ungelesen-Zahl. Das ist die
 * unterstützte Alternative zum (für Dritt-Apps gesperrten) „–1"-Bildschirm.
 *
 * Aktualisiert sich über [android.R.attr.updatePeriodMillis] (halbstündlich) sowie sofort,
 * wenn eine neue Nachricht eintrifft (siehe [requestUpdate], aufgerufen vom Listener).
 */
class HubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.messageRepository(appContext)
                val messages = repo.inboxSnapshot(ROW_COUNT)
                val unread = repo.unreadCount()
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(appContext, messages, unread))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, messages: List<MessageEntity>, unread: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hub)

        views.setTextViewText(R.id.widget_count, if (unread > 0) "$unread neu" else "")

        val rowIds = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2)
        views.setViewVisibility(R.id.widget_empty, if (messages.isEmpty()) View.VISIBLE else View.GONE)

        rowIds.forEachIndexed { index, rowId ->
            val message = messages.getOrNull(index)
            if (message == null) {
                views.setViewVisibility(rowId, View.GONE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                // "Absender · Text" – kompakt in einer Zeile.
                views.setTextViewText(rowId, "${message.sender}: ${message.content.take(80)}")
            }
        }

        // Tippen öffnet Hub.
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    companion object {
        private const val ROW_COUNT = 3

        /** Fordert eine sofortige Aktualisierung aller Hub-Widgets an (z. B. nach neuem Ingest). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HubWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, HubWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
