package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Zustand einer laufenden bzw. abgeschlossenen Quick-Reply-Zustellung. */
sealed interface QuickReplyState {
    data object Idle : QuickReplyState
    data object Sending : QuickReplyState
    data object Sent : QuickReplyState
    data class Failed(val reason: String) : QuickReplyState
}

/**
 * Antwortfeld im Peek-Sheet. Wird nur eingeblendet, wenn für die Nachricht aktuell eine
 * gültige RemoteInput-Action vorliegt – siehe
 * [com.hub.app.notification.NotificationMessageSource.canReplyTo].
 */
@Composable
fun QuickReplyBar(
    state: QuickReplyState,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val sending = state is QuickReplyState.Sending

    // Eingabe erst leeren, wenn die Zustellung geklappt hat - bei einem Fehler bleibt
    // der Text stehen, damit der Nutzer ihn nicht neu tippen muss.
    LaunchedEffect(state) {
        if (state is QuickReplyState.Sent) text = ""
    }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Antworten …") },
                enabled = !sending,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) onSend(text)
                })
            )
            Spacer(Modifier.width(8.dp))
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp))
            } else {
                IconButton(
                    onClick = { if (text.isNotBlank()) onSend(text) },
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Senden",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when (state) {
            is QuickReplyState.Sent -> StatusLine("Gesendet", MaterialTheme.colorScheme.primary)
            is QuickReplyState.Failed -> StatusLine(state.reason, MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(6.dp))
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}
