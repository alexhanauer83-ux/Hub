package com.hub.app.ui.hub

import android.content.Intent
import android.media.RingtoneManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.MessageEntity

/** Auswahl des Kanals für eine neue Nachricht (universeller „Neu"-Einstieg). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewMessageSheet(
    telegramAvailable: Boolean,
    onDismiss: () -> Unit,
    onSms: () -> Unit,
    onMatrix: () -> Unit,
    onTelegram: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Neue Nachricht",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("SMS") },
                leadingContent = { Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = onSms)
            )
            ListItem(
                headlineContent = { Text("Matrix-Chat") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = onMatrix)
            )
            if (telegramAvailable) {
                ListItem(
                    headlineContent = { Text("Telegram") },
                    // Bots können keinen Erstkontakt aufbauen – daher: bekannten Chat wählen.
                    supportingContent = { Text("Bestehenden Chat wählen") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(onClick = onTelegram)
                )
            }
        }
    }
}

/** Einmaliger, wegtippbarer Hinweis auf die Feed-Gesten. */
@Composable
internal fun GestureHintCard(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("So geht's schnell", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tippen = Antworten · Doppeltippen = App öffnen · Lang drücken = Menü\n" +
                        "Wischen → als gelesen · Wischen ← archivieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Hinweis ausblenden", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Chips mit den zuletzt genutzten Suchbegriffen (erscheinen bei leerem Suchfeld). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Zuletzt gesucht",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Löschen") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { query ->
                AssistChip(
                    onClick = { onPick(query) },
                    label = { Text(query) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

/** Moderner Leerzustand: großes, dezentes Icon über einer kurzen Erklärung. */
@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun AccessBanner(onOpenOnboarding: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Benachrichtigungszugriff fehlt", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ohne diese Berechtigung kann Hub keine Nachrichten sammeln.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpenOnboarding) { Text("Einrichten") }
    }
}

@Composable
internal fun ConversationList(
    conversations: List<ConversationSummary>,
    listState: LazyListState,
    onOpen: (ConversationRef) -> Unit,
    onTogglePin: (ConversationSummary) -> Unit
) {
    if (conversations.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.Chat, "Keine Unterhaltungen")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(conversations, key = { it.sourceKey + "/" + it.groupValue }) { conversation ->
            // Sanftes Verschieben, wenn Unterhaltungen dazukommen/wegfallen (moderne Motion).
            Column(Modifier.animateItem()) {
                ConversationRow(
                    conversation = conversation,
                    onClick = { onOpen(conversation.ref) },
                    onLongClick = { onTogglePin(conversation) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
internal fun MessageList(
    messages: List<MessageEntity>,
    listState: LazyListState,
    isArchiveView: Boolean,
    emptyHint: String,
    emptyIcon: ImageVector,
    onOpen: (MessageEntity) -> Unit,
    onReply: (MessageEntity) -> Unit,
    onMarkRead: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    rightAction: com.hub.app.notification.SwipeAction,
    leftAction: com.hub.app.notification.SwipeAction,
    onOpenPeek: (MessageEntity) -> Unit,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    showDateDividers: Boolean = false
) {
    if (messages.isEmpty()) {
        EmptyState(emptyIcon, emptyHint)
        return
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            // Sanftes Verschieben, wenn Zeilen dazukommen/wegfallen (gelesen/archiviert) –
            // moderne Motion nach Material 3.
            Column(Modifier.animateItem()) {
                // Datums-Trenner (nur im Chatverlauf): über der ersten Nachricht eines Tages.
                if (showDateDividers) {
                    val prev = messages.getOrNull(index - 1)
                    if (prev == null || !isSameDay(prev.timestamp, message.timestamp)) {
                        DateDivider(message.timestamp)
                    }
                }
                SwipeableMessageRow(
                    message = message,
                    isArchiveView = isArchiveView,
                    onMarkRead = { onMarkRead(message.id) },
                    onArchive = { onArchive(message.id) },
                    onUnarchive = { onUnarchive(message.id) },
                    onDelete = { onDelete(message.id) },
                    rightAction = rightAction,
                    leftAction = leftAction,
                    // Kurz tippen = Antworten (Vorschau/Antwortfeld), doppelt tippen = App
                    // öffnen, lange drücken = Menü (Peek mit allen Aktionen inkl. App öffnen).
                    onClick = { onReply(message) },
                    onDoubleClick = { onOpen(message) },
                    onLongPress = { onOpenPeek(message) },
                    onReply = { onReply(message) },
                    selectionActive = selectionActive,
                    selected = message.id in selectedIds,
                    onToggleSelect = { onToggleSelect(message.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DateDivider(timestamp: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateLabel(timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun dateLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> "Heute"
        isSameDay(timestamp, now - 24L * 60 * 60 * 1000) -> "Gestern"
        else -> java.text.SimpleDateFormat("EEE, dd.MM.yyyy", java.util.Locale.GERMANY)
            .format(java.util.Date(timestamp))
    }
}

/** Baut den System-Dialog zur Ton-Auswahl (Benachrichtigungstöne), mit aktueller Vorauswahl. */
internal fun buildRingtonePickerIntent(currentUri: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ton wählen")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            currentUri?.let { android.net.Uri.parse(it) }
        )
    }

/** E-Mail-Nachricht? (IMAP-Quellen haben den sourceKey-Präfix „imap:".) */
internal fun isEmail(message: MessageEntity): Boolean =
    message.sourceKey.startsWith(com.hub.app.connectors.imap.ImapConnector.SOURCE_KEY_PREFIX)

internal fun emptyHintFor(tab: HubTab): String = when (tab) {
    HubTab.POSTEINGANG -> "Keine ungelesenen Nachrichten"
    HubTab.PRIORITAET -> "Noch nichts priorisiert.\nHalte eine Nachricht gedrückt, um sie als wichtig zu markieren."
    HubTab.ARCHIV -> "Archiv ist leer"
}
