package com.hub.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.notification.SwipeAction

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
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    rightAction: SwipeAction = SwipeAction.READ,
    leftAction: SwipeAction = SwipeAction.ARCHIVE,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentOnMarkRead by rememberUpdatedState(onMarkRead)
    val currentOnArchive by rememberUpdatedState(onArchive)
    val currentOnUnarchive by rememberUpdatedState(onUnarchive)
    val currentOnDelete by rememberUpdatedState(onDelete)
    // rememberUpdatedState, damit die einmal gemerkte confirmValueChange-Closure die aktuell
    // konfigurierten Aktionen liest (Änderung in den Einstellungen wirkt ohne Neuanlage).
    val currentRight by rememberUpdatedState(rightAction)
    val currentLeft by rememberUpdatedState(leftAction)
    // Fühlbare Bestätigung beim Auslösen einer Wisch-Aktion (moderne Haptik).
    val view = LocalView.current

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
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentRight
                SwipeToDismissBoxValue.EndToStart -> currentLeft
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            if (action != SwipeAction.NONE) {
                view.confirmHaptic()
                when (action) {
                    SwipeAction.READ -> currentOnMarkRead()
                    // Im Archiv kehrt „Archivieren" zu „Wiederherstellen" um.
                    SwipeAction.ARCHIVE -> if (isArchiveView) currentOnUnarchive() else currentOnArchive()
                    SwipeAction.DELETE -> currentOnDelete()
                    SwipeAction.NONE -> Unit
                }
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
        // Richtungen mit Aktion „Nichts" gar nicht erst wischbar.
        enableDismissFromStartToEnd = rightAction != SwipeAction.NONE,
        enableDismissFromEndToStart = leftAction != SwipeAction.NONE,
        backgroundContent = {
            SwipeBackground(state.dismissDirection, rightAction, leftAction, isArchiveView)
        }
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

/** Kurze Bestätigungs-Haptik; nutzt das modernere CONFIRM ab Android 11, sonst einen Tick. */
private fun View.confirmHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(constant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    rightAction: SwipeAction,
    leftAction: SwipeAction,
    isArchiveView: Boolean
) {
    val action = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> rightAction
        SwipeToDismissBoxValue.EndToStart -> leftAction
        SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
    }
    val color = when (action) {
        SwipeAction.READ -> MaterialTheme.colorScheme.primary
        SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.surfaceVariant
        SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
        SwipeAction.NONE -> MaterialTheme.colorScheme.background
    }
    val tint = when (action) {
        SwipeAction.READ -> MaterialTheme.colorScheme.onPrimary
        SwipeAction.DELETE -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val icon = when (action) {
        SwipeAction.READ -> Icons.Default.MarkEmailRead
        SwipeAction.ARCHIVE -> if (isArchiveView) Icons.Default.Unarchive else Icons.Default.Archive
        SwipeAction.DELETE -> Icons.Default.Delete
        SwipeAction.NONE -> null
    }
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
