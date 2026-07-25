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
                val unread = runCatching { ServiceLocator.messageRepository(appContext).unreadCount() }.getOrDefault(0)
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(appContext, id, unread))
                    // Liste neu laden lassen (RemoteViewsFactory.onDataSetChanged).
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, widgetId: Int, unread: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hub_list)
        views.setTextViewText(R.id.widget_count, if (unread > 0) "$unread neu" else "")

        // Scrollbare Liste an den RemoteViewsService binden.
        val serviceIntent = Intent(context, HubWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Eindeutige data-URI je Widget, sonst cachet das System den Adapter.
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // Template: Tippen auf einen Eintrag öffnet Hub.
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val template = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, template)

        return views
    }

    companion object {
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
