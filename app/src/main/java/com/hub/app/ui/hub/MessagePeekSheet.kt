package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MarkEmailRead
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
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Peek": Long-Press auf eine Feed-Zeile zeigt den vollständigen Text plus die
 * wichtigsten Aktionen, **ohne** die Quell-App zu öffnen – das war der Kern des
 * BlackBerry-Hub-Gefühls. Quick Reply wird in Phase 4 hier ergänzt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagePeekSheet(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onTogglePriority: () -> Unit,
    onAlwaysPrioritizeSender: () -> Unit,
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
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PeekAction(
                    icon = if (message.priority) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (message.priority) "Priorität aus" else "Priorität",
                    onClick = onTogglePriority
                )
                if (!message.isRead) {
                    PeekAction(Icons.Default.MarkEmailRead, "Gelesen", onMarkRead)
                }
                PeekAction(Icons.Default.Archive, "Archivieren", onArchive)
            }

            TextButton(onClick = onAlwaysPrioritizeSender) {
                Text("„${message.sender}“ immer priorisieren")
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
