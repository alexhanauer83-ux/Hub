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
import androidx.compose.runtime.LaunchedEffect
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
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnMarkRead by rememberUpdatedState(onMarkRead)
    val currentOnArchive by rememberUpdatedState(onArchive)
    val currentOnUnarchive by rememberUpdatedState(onUnarchive)

    val state = rememberSwipeToDismissBoxState(
        // Die Aktion wird nicht in confirmValueChange ausgelöst, weil dieser Callback
        // auch beim blossen Vorbeiziehen feuern kann. Stattdessen unten per LaunchedEffect
        // auf den tatsächlich erreichten Endzustand reagieren.
        positionalThreshold = { distance -> distance * 0.35f }
    )

    LaunchedEffect(state.currentValue, message.id) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                currentOnMarkRead()
                // Zeile bleibt sichtbar (nur "gelesen"), daher zurücksetzen.
                state.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                if (isArchiveView) currentOnUnarchive() else currentOnArchive()
                // Die Zeile verschwindet aus dem aktuellen Flow; zurücksetzen, damit ein
                // recycelter Slot nicht im Dismiss-Zustand hängen bleibt.
                state.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state.dismissDirection, isArchiveView) }
    ) {
        MessageRow(
            message = message,
            onClick = onClick,
            onLongPress = onLongPress
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
