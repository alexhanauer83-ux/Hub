package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hub.app.data.local.entity.MessageEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    onOpenOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickReplyState by viewModel.quickReplyState.collectAsStateWithLifecycle()
    var peekMessage by remember { mutableStateOf<MessageEntity?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Titel spiegelt die aktive Auswahl: gewählte Quelle > Priority Hub > Posteingang.
    val title = when {
        state.sourceFilter != null ->
            state.sources.firstOrNull { it.sourceKey == state.sourceFilter }?.label ?: "Hub"
        state.tab == HubTab.PRIORITAET -> "Priority Hub"
        else -> "Hub"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SourceDrawer(
                sources = state.sources,
                sourceCounts = state.sourceCounts,
                selectedSourceKey = state.sourceFilter,
                onSelectSource = { key ->
                    viewModel.selectSourceFilter(key)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        // Menü öffnet sich auch per Wisch von links (ModalNavigationDrawer-Geste).
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Quellen")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.selectTab(
                                if (state.tab == HubTab.PRIORITAET) HubTab.POSTEINGANG else HubTab.PRIORITAET
                            )
                        }) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Priority Hub",
                                tint = if (state.tab == HubTab.PRIORITAET) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Einstellungen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (!state.hasNotificationAccess) {
                    AccessBanner(onOpenOnboarding)
                }

                // Ansichts-Tabs nur ohne aktiven Quellenfilter; ist eine App gewählt, zeigt
                // der Feed alle ihre Nachrichten.
                if (state.sourceFilter == null) {
                    HubFilterBar(
                        selectedTab = state.tab,
                        onSelectTab = viewModel::selectTab
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                MessageList(
                    messages = state.messages,
                    isArchiveView = state.tab == HubTab.ARCHIV && state.sourceFilter == null,
                    emptyHint = if (state.sourceFilter != null) {
                        "Keine Nachrichten dieser App"
                    } else {
                        emptyHintFor(state.tab)
                    },
                    onMarkRead = viewModel::markRead,
                    onArchive = viewModel::archive,
                    onUnarchive = viewModel::unarchive,
                    onOpenPeek = { peekMessage = it }
                )
            }
        }
    }

    peekMessage?.let { message ->
        // Verfügbarkeit der Antwort-Action einmal pro Öffnen prüfen, nicht bei jeder
        // Recomposition - der Zustand kann sich nur zwischen zwei Peeks ändern.
        val canReply = remember(message.id) { viewModel.canQuickReply(message) }
        val closePeek = {
            peekMessage = null
            viewModel.resetQuickReplyState()
        }

        MessagePeekSheet(
            message = message,
            canQuickReply = canReply,
            quickReplyState = quickReplyState,
            onSendQuickReply = { text -> viewModel.sendQuickReply(message, text) },
            onDismiss = closePeek,
            onMarkRead = {
                viewModel.markRead(message.id)
                closePeek()
            },
            onArchive = {
                viewModel.archive(message.id)
                closePeek()
            },
            onTogglePriority = {
                viewModel.setPriority(message.id, !message.priority)
                closePeek()
            },
            onAlwaysPrioritizeSender = {
                viewModel.addPriorityContact(message)
                closePeek()
            }
        )
    }
}

@Composable
private fun AccessBanner(onOpenOnboarding: () -> Unit) {
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
private fun MessageList(
    messages: List<MessageEntity>,
    isArchiveView: Boolean,
    emptyHint: String,
    onMarkRead: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onOpenPeek: (MessageEntity) -> Unit
) {
    if (messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { message ->
            SwipeableMessageRow(
                message = message,
                isArchiveView = isArchiveView,
                onMarkRead = { onMarkRead(message.id) },
                onArchive = { onArchive(message.id) },
                onUnarchive = { onUnarchive(message.id) },
                // Antippen markiert als gelesen; den vollen Text gibt es per Long-Press,
                // ohne die Quell-App zu oeffnen.
                onClick = { onMarkRead(message.id) },
                onLongPress = { onOpenPeek(message) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun emptyHintFor(tab: HubTab): String = when (tab) {
    HubTab.POSTEINGANG -> "Keine ungelesenen Nachrichten"
    HubTab.PRIORITAET -> "Noch nichts priorisiert.\nHalte eine Nachricht gedrückt, um sie als wichtig zu markieren."
    HubTab.ARCHIV -> "Archiv ist leer"
}
