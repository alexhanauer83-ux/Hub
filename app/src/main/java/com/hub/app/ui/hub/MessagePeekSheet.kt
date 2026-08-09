package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hub.app.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * "Peek": Long-Press auf eine Feed-Zeile zeigt den vollständigen Text plus die
 * wichtigsten Aktionen, **ohne** die Quell-App zu öffnen – das war der Kern des
 * BlackBerry-Hub-Gefühls. Quick Reply wird in Phase 4 hier ergänzt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessagePeekSheet(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onArchive: () -> Unit,
    onTogglePriority: () -> Unit,
    onAlwaysPrioritizeSender: () -> Unit,
    onOpenApp: () -> Unit,
    onDelete: () -> Unit,
    onChooseSound: () -> Unit,
    onSnooze: (Long) -> Unit,
    onSelect: () -> Unit,
    canQuickReply: Boolean,
    quickReplyState: QuickReplyState,
    onSendQuickReply: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(message.sender, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                "${message.sourceLabel} · ${fullTimestamp(message.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Betreff (z. B. E-Mail) hervorgehoben über dem Textkörper.
            message.subject?.takeIf { it.isNotBlank() }?.let { subject ->
                Spacer(Modifier.height(12.dp))
                Text(subject, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (message.isContentRedacted) {
                    "Android hat den Inhalt dieser Benachrichtigung ausgeblendet " +
                        "(Einstellung „Sensible Inhalte“). Hub kann ihn nicht anzeigen."
                } else {
                    message.content
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isContentRedacted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            )

            message.imageUri?.let { uri ->
                Spacer(Modifier.height(16.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = "Bildanhang",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            message.audioUri?.let { uri ->
                Spacer(Modifier.height(16.dp))
                AudioPlayer(audioUri = uri)
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PeekAction(Icons.Default.OpenInNew, "App öffnen", onOpenApp)
                if (message.isRead) {
                    PeekAction(Icons.Default.MarkEmailUnread, "Ungelesen", onMarkUnread)
                } else {
                    PeekAction(Icons.Default.MarkEmailRead, "Gelesen", onMarkRead)
                }
                PeekAction(Icons.Default.Archive, "Archivieren", onArchive)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PeekAction(
                    icon = if (message.priority) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (message.priority) "Priorität aus" else "Priorität",
                    onClick = onTogglePriority
                )
                PeekAction(Icons.Default.Delete, "Löschen", onDelete)
            }

            TextButton(onClick = onAlwaysPrioritizeSender) {
                Text("„${message.sender}“ immer priorisieren")
            }
            TextButton(onClick = onChooseSound) {
                Text("Ton für „${message.sender}“ wählen")
            }
            TextButton(onClick = onSelect) {
                Text("Mehrere auswählen")
            }

            // Snooze: kurz zurückstellen, taucht danach wieder auf. Neben festen Dauern gibt es
            // „smarte" Zeitpunkte (heute Abend / morgen früh / nächste Woche) wie beim Hub-Vorbild.
            Spacer(Modifier.height(4.dp))
            Text("Später erinnern", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSnooze(60 * 60 * 1000L) }) { Text("1 Std") }
                TextButton(onClick = { onSnooze(3 * 60 * 60 * 1000L) }) { Text("3 Std") }
                // „Heute Abend" nur anbieten, wenn 18 Uhr noch nicht vorbei ist.
                if (millisUntilTonight() > 0L) {
                    TextButton(onClick = { onSnooze(millisUntilTonight()) }) { Text("Heute Abend") }
                }
                TextButton(onClick = { onSnooze(millisUntilTomorrowMorning()) }) { Text("Morgen früh") }
                TextButton(onClick = { onSnooze(millisUntilNextWeek()) }) { Text("Nächste Woche") }
            }

            if (canQuickReply) {
                Spacer(Modifier.height(8.dp))
                QuickReplyBar(state = quickReplyState, onSend = onSendQuickReply)
            } else if (message.hasQuickReply) {
                // Die Notification hatte einmal eine Antwort-Action, die PendingIntent ist
                // aber nicht mehr gueltig (Notification verworfen oder Geraet neu gestartet).
                Spacer(Modifier.height(8.dp))
                Text(
                    "Direktantwort nicht mehr möglich – öffne die App, um zu antworten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeekAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

private fun fullTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(timestamp))

/** Millisekunden von jetzt bis heute 18:00 (negativ, wenn schon vorbei). */
private fun millisUntilTonight(): Long =
    atTime(days = 0, hour = 18) - System.currentTimeMillis()

/** Millisekunden von jetzt bis morgen 08:00. */
private fun millisUntilTomorrowMorning(): Long =
    atTime(days = 1, hour = 8) - System.currentTimeMillis()

/** Millisekunden von jetzt bis zum nächsten Montag 08:00. */
private fun millisUntilNextWeek(): Long {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    // Mindestens einen Tag weiter, dann bis zum nächsten Montag.
    do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
    return c.timeInMillis - System.currentTimeMillis()
}

/** Zeitpunkt in [days] Tagen um [hour]:00 Uhr als Millis. */
private fun atTime(days: Int, hour: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
