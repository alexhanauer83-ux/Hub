package com.hub.app.ui.hub

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
    onOpenMatrix: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickReplyState by viewModel.quickReplyState.collectAsStateWithLifecycle()
    var peekMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var replyMessage by remember { mutableStateOf<MessageEntity?>(null) }

    // Merkt sich, für welche Nachricht gerade ein Ton gewählt wird (Ergebnis kommt async).
    var soundPickMessage by remember { mutableStateOf<MessageEntity?>(null) }
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: android.net.Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        soundPickMessage?.let { viewModel.setSenderSound(it, uri?.toString()) }
        soundPickMessage = null
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Titel spiegelt die aktive Auswahl: Unterhaltung > Quelle > Priority Hub > Posteingang.
    val title = when {
        state.conversationFilter != null -> state.conversationFilter!!.title
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
                    title = {
                        if (state.isSearching) {
                            TextField(
                                value = state.searchQuery.orEmpty(),
                                onValueChange = viewModel::setSearchQuery,
                                placeholder = { Text("Suchen …") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        } else {
                            Text(title)
                        }
                    },
                    navigationIcon = {
                        when {
                            state.isSearching -> IconButton(onClick = { viewModel.stopSearch() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Suche schließen")
                            }
                            state.conversationFilter != null -> IconButton(onClick = { viewModel.closeConversation() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                            else -> IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Quellen")
                            }
                        }
                    },
                    actions = {
                      // Im Suchmodus keine weiteren Aktionen (Fokus auf dem Suchfeld).
                      if (!state.isSearching) {
                        IconButton(onClick = { viewModel.startSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Suchen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        IconButton(onClick = onOpenMatrix) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Matrix",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Einstellungen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                // Tabs + Gruppieren-Umschalter nur in der Übersicht (kein Quellen-/Konversationsfilter).
                if (state.sourceFilter == null && state.conversationFilter == null) {
                    HubFilterBar(
                        selectedTab = state.tab,
                        onSelectTab = viewModel::selectTab
                    )
                    if (state.tab == HubTab.POSTEINGANG) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            androidx.compose.material3.FilterChip(
                                selected = state.grouped,
                                onClick = { viewModel.setGrouped(!state.grouped) },
                                label = { Text(if (state.grouped) "Gruppiert" else "Einzeln") }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                if (state.showConversations) {
                    ConversationList(
                        conversations = state.conversations,
                        onOpen = viewModel::openConversation
                    )
                    return@Column
                }

                MessageList(
                    messages = state.messages,
                    isArchiveView = state.tab == HubTab.ARCHIV && state.sourceFilter == null && state.conversationFilter == null,
                    emptyHint = when {
                        state.conversationFilter != null -> "Keine Nachrichten in dieser Unterhaltung"
                        state.sourceFilter != null -> "Keine Nachrichten dieser App"
                        else -> emptyHintFor(state.tab)
                    },
                    onOpen = viewModel::openMessage,
                    onReply = { replyMessage = it },
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
        val canReply = remember(message.id) { viewModel.canReply(message) }
        val closePeek = {
            peekMessage = null
            viewModel.resetQuickReplyState()
        }

        MessagePeekSheet(
            message = message,
            canQuickReply = canReply,
            quickReplyState = quickReplyState,
            onSendQuickReply = { text -> viewModel.sendReply(message, text) },
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
            },
            onOpenApp = {
                viewModel.openMessage(message)
                closePeek()
            },
            onDelete = {
                viewModel.delete(message.id)
                closePeek()
            },
            onChooseSound = {
                soundPickMessage = message
                ringtonePicker.launch(buildRingtonePickerIntent(viewModel.currentSenderSound(message)))
                closePeek()
            }
        )
    }

    replyMessage?.let { message ->
        val canReply = remember(message.id) { viewModel.canReply(message) }
        ReplySheet(
            message = message,
            canReply = canReply,
            state = quickReplyState,
            onSend = { text -> viewModel.sendReply(message, text) },
            onDismiss = {
                replyMessage = null
                viewModel.resetQuickReplyState()
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
private fun ConversationList(
    conversations: List<ConversationSummary>,
    onOpen: (ConversationRef) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Keine Unterhaltungen",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(conversations, key = { it.sourceKey + "/" + it.groupValue }) { conversation ->
            ConversationRow(conversation = conversation, onClick = { onOpen(conversation.ref) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageEntity>,
    isArchiveView: Boolean,
    emptyHint: String,
    onOpen: (MessageEntity) -> Unit,
    onReply: (MessageEntity) -> Unit,
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
                // Kurz tippen = Antworten (Vorschau/Antwortfeld), doppelt tippen = App
                // öffnen, lange drücken = Menü (Peek mit allen Aktionen inkl. App öffnen).
                onClick = { onReply(message) },
                onDoubleClick = { onOpen(message) },
                onLongPress = { onOpenPeek(message) },
                onReply = { onReply(message) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Baut den System-Dialog zur Ton-Auswahl (Benachrichtigungstöne), mit aktueller Vorauswahl. */
private fun buildRingtonePickerIntent(currentUri: String?): Intent =
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

private fun emptyHintFor(tab: HubTab): String = when (tab) {
    HubTab.POSTEINGANG -> "Keine ungelesenen Nachrichten"
    HubTab.PRIORITAET -> "Noch nichts priorisiert.\nHalte eine Nachricht gedrückt, um sie als wichtig zu markieren."
    HubTab.ARCHIV -> "Archiv ist leer"
}
