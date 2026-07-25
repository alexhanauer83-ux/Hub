package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.MessageEntity

/**
 * Kompaktes Antwort-Fenster, das direkt über den Antwort-Button einer Nachricht öffnet –
 * ohne die Quell-App zu wechseln. Nutzt dieselbe [QuickReplyBar] wie die Peek-Vorschau
 * und dasselbe Routing im ViewModel (Notification-RemoteInput, Matrix, Telegram, SMS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(
    message: MessageEntity,
    state: QuickReplyState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
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
            Text(
                "Antwort an ${message.sender}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            QuickReplyBar(state = state, onSend = onSend)
        }
    }
}
