package com.hub.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.MessageEntity

/**
 * BlackBerry-Hub-Gestik: Swipe nach **rechts** = als gelesen markieren,
 * Swipe nach **links** = archivieren. Im Archiv-Tab kehrt der Links-Swipe seine
 * Bedeutung zu "wiederherstellen" um, da erneutes Archivieren dort sinnlos wäre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMessageRow(
    message: MessageEntity,
    isArchiveView: Boolean,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentOnMarkRead by rememberUpdatedState(onMarkRead)
    val currentOnArchive by rememberUpdatedState(onArchive)
    val currentOnUnarchive by rememberUpdatedState(onUnarchive)

    // Aktion direkt in confirmValueChange auslösen und IMMER false zurückgeben: Die Aktion
    // ändert den DB-Zustand, woraufhin der Flow den Eintrag aus der Liste entfernt
    // (gelesen -> aus dem Posteingang ausgeblendet, archiviert -> ins Archiv). Die Box
    // verharrt so nie in einem "dismissed"-Zustand.
    //
    // Der frühere Ansatz (LaunchedEffect auf state.currentValue + state.reset()) hat beim
    // Archivieren versagt: Sobald die Aktion die Zeile aus der Liste entfernte, wurde die
    // Composable samt LaunchedEffect verworfen und reset()/die Aktion mittendrin abgebrochen
    // - deshalb "wischen ins Archiv ging nicht", Peek-Archivieren aber schon.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentOnMarkRead()
                SwipeToDismissBoxValue.EndToStart ->
                    if (isArchiveView) currentOnUnarchive() else currentOnArchive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    // Im Auswahl-Modus keine Swipe-Gesten (Tippen wählt aus/ab).
    if (selectionActive) {
        MessageRow(
            message = message,
            modifier = modifier,
            selected = selected,
            onClick = onToggleSelect
        )
        return
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, isArchiveView) }
    ) {
        MessageRow(
            message = message,
            onClick = onClick,
            onDoubleClick = onDoubleClick,
            onLongPress = onLongPress,
            onReply = onReply
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, isArchiveView: Boolean) {
    val isRead = direction == SwipeToDismissBoxValue.StartToEnd
    val color = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.surfaceVariant
        SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.background
    }
    val icon = when {
        isRead -> Icons.Default.MarkEmailRead
        isArchiveView -> Icons.Default.Unarchive
        else -> Icons.Default.Archive
    }
    val alignment = if (isRead) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isRead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
