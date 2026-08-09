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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hub.app.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vollwertige Lese-Ansicht für E-Mails: großer Betreff, Absender-Kopf und der komplette,
 * scrollbare Mailtext mit auswählbarem Inhalt – statt der knappen Feed-Zeile. Antworten ist
 * bewusst nicht dabei (IMAP ist reiner Empfang, kein SMTP-Versand).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailReaderSheet(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onTogglePriority: () -> Unit
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
            // Betreff als kräftige Überschrift.
            Text(
                text = message.subject?.takeIf { it.isNotBlank() } ?: "(Kein Betreff)",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(14.dp))

            // Absender-Kopf: Avatar + Name + Konto/Datum.
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceAvatar(
                    sourceKey = message.sourceKey,
                    packageName = message.sourcePackageName,
                    title = message.sender
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        message.sender,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${message.sourceLabel} · ${fullTimestamp(message.timestamp)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(14.dp))

            // Voller Mailtext: auswählbar und mit angenehmem Zeilenabstand, eigener Scrollbereich.
            SelectionContainer {
                Text(
                    text = if (message.isContentRedacted) {
                        "Android hat den Inhalt dieser Benachrichtigung ausgeblendet " +
                            "(Einstellung „Sensible Inhalte“)."
                    } else {
                        message.content
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = if (message.isContentRedacted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            message.imageUri?.let { uri ->
                Spacer(Modifier.height(16.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = "Bildanhang",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReaderAction(
                    icon = if (message.priority) Icons.Default.Star else Icons.Default.StarBorder,
                    label = if (message.priority) "Priorität aus" else "Priorität",
                    onClick = onTogglePriority
                )
                ReaderAction(Icons.Default.Archive, "Archivieren", onArchive)
                ReaderAction(Icons.Default.Delete, "Löschen", onDelete)
            }
        }
    }
}

@Composable
private fun ReaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, textAlign = TextAlign.Center)
    }
}

private fun fullTimestamp(timestamp: Long): String =
    SimpleDateFormat("EEE, dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(timestamp))
