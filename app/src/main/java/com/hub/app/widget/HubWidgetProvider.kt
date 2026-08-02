package com.hub.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hub.app.MainActivity
import com.hub.app.R
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
                val messages = runCatching { repo.inboxSnapshot(ROW_LIMIT * 3) }.getOrDefault(emptyList())
                val unread = runCatching { repo.unreadCount() }.getOrDefault(0)
                // Pro Unterhaltung die neueste Nachricht (wie in der gruppierten Uebersicht).
                val rows = messages
                    .groupBy { it.sourceKey to (it.conversationId?.takeIf { c -> c.isNotBlank() } ?: it.sender) }
                    .map { (_, msgs) -> msgs.first() }
                    .sortedByDescending { it.timestamp }
                    .take(ROW_LIMIT)

                val views = buildViews(appContext, unread, rows)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(
        context: Context,
        unread: Int,
        rows: List<com.hub.app.data.local.entity.MessageEntity>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hub_simple)
        views.setTextViewText(R.id.widget_count, if (unread > 0) "$unread neu" else "")

        views.removeAllViews(R.id.widget_container)
        views.setViewVisibility(R.id.widget_empty, if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)

        for (message in rows) {
            val item = RemoteViews(context.packageName, R.layout.widget_item)
            val title = (message.conversationId?.takeIf { it.isNotBlank() } ?: message.sender)
            item.setTextViewText(R.id.item_title, title)
            item.setTextViewText(R.id.item_text, message.subject ?: message.content)
            item.setTextViewText(R.id.item_time, formatTime(message.timestamp))
            views.addView(R.id.widget_container, item)
        }

        // Tippen aufs Widget oeffnet Hub.
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        return views
    }

    private fun formatTime(timestamp: Long): String {
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
            now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "dd.MM."
        return java.text.SimpleDateFormat(pattern, java.util.Locale.GERMANY).format(java.util.Date(timestamp))
    }

    companion object {
        private const val ROW_LIMIT = 8

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
