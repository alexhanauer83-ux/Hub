package com.hub.app.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hub.app.R
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Liefert die scrollbaren Listeneinträge für das Hub-Widget. */
class HubWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        HubWidgetFactory(applicationContext)
}

private class HubWidgetFactory(
    private val context: android.content.Context
) : RemoteViewsService.RemoteViewsFactory {

    private data class Item(val title: String, val text: String, val time: Long)

    private var items: List<Item> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Läuft auf einem Binder-Thread (nicht Main) -> runBlocking ist hier vertretbar.
        items = runBlocking {
            withContext(Dispatchers.IO) {
                val repo = ServiceLocator.messageRepository(context)
                val messages = repo.inboxSnapshot(60)
                // Pro Unterhaltung (Konversationstitel bzw. Absender) die neueste Nachricht.
                messages.groupBy { it.sourceKey to groupValue(it) }
                    .map { (_, msgs) ->
                        val latest = msgs.first() // Snapshot ist nach Zeit absteigend
                        val unread = msgs.count { !it.isRead }
                        Item(
                            title = groupValue(latest) + if (unread > 0) "  ($unread)" else "",
                            text = latest.content.ifBlank { if (latest.imageUri != null) "📷 Bild" else "" },
                            time = latest.timestamp
                        )
                    }
                    .sortedByDescending { it.time }
            }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_item)
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            setTextViewText(R.id.item_title, item.title)
            setTextViewText(R.id.item_text, item.text)
            setTextViewText(R.id.item_time, formatTime(item.time))
            // Fill-in Intent: Tippen auf einen Eintrag öffnet Hub (Template im Provider).
            setOnClickFillInIntent(R.id.item_root, Intent())
        }
    }

    override fun getCount(): Int = items.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() { items = emptyList() }

    private fun groupValue(m: MessageEntity): String =
        m.conversationId?.takeIf { it.isNotBlank() } ?: m.sender

    private fun formatTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "dd.MM."
        return SimpleDateFormat(pattern, Locale.GERMANY).format(Date(timestamp))
    }
}
