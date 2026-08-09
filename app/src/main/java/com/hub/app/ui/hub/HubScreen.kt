package com.hub.app.ui.hub

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onComposeSms: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickReplyState by viewModel.quickReplyState.collectAsStateWithLifecycle()
    val selection by viewModel.selectionState.collectAsStateWithLifecycle()
    var peekMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var replyMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var emailMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    var composeSheetOpen by remember { mutableStateOf(false) }
    // Scrollzustand des Feeds – der „Neu"-FAB kollabiert beim Scrollen zum Icon (modern).
    val listState = rememberLazyListState()

    // E-Mails bekommen eine eigene, schöne Lese-Ansicht statt Antwort-/App-Weg.
    val openEmail: (MessageEntity) -> Unit = { msg ->
        emailMessage = msg
        viewModel.markRead(msg.id)
    }

    // Snackbar für widerrufbare Aktionen (Archivieren/Löschen/Gelesen …).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { request ->
            val result = snackbarHostState.showSnackbar(
                message = request.label,
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) request.undo()
        }
    }

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
                pinnedSources = state.pinnedSources,
                onSelectSource = { key ->
                    viewModel.selectSourceFilter(key)
                    scope.launch { drawerState.close() }
                },
                onTogglePin = viewModel::togglePinnedSource
            )
        }
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        if (selection.active) {
                            Text("${selection.ids.size} ausgewählt")
                        } else if (state.isSearching) {
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
                            selection.active -> IconButton(onClick = { viewModel.exitSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Auswahl beenden")
                            }
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
                      if (selection.active) {
                          // Sammelaktionen für die ausgewählten Nachrichten.
                          IconButton(onClick = { viewModel.markSelectedRead() }) {
                              Icon(Icons.Default.MarkEmailRead, contentDescription = "Als gelesen")
                          }
                          IconButton(onClick = { viewModel.archiveSelected() }) {
                              Icon(Icons.Default.Archive, contentDescription = "Archivieren")
                          }
                          IconButton(onClick = { viewModel.deleteSelected() }) {
                              Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                          }
                      } else if (!state.isSearching) {
                        // Im Suchmodus keine weiteren Aktionen (Fokus auf dem Suchfeld).
                        // Nur zwei sichtbare Aktionen; alles Seltenere liegt im ⋮-Menü.
                        val inboxRoot = state.tab == HubTab.POSTEINGANG &&
                            state.conversationFilter == null && state.sourceFilter == null

                        // Neuer Matrix-Chat, nur im Matrix-Reiter direkt sichtbar.
                        if (state.sourceFilter == com.hub.app.connectors.matrix.MatrixConnector.SOURCE_KEY &&
                            state.conversationFilter == null
                        ) {
                            IconButton(onClick = onOpenMatrix) {
                                Icon(Icons.Default.Add, contentDescription = "Neuer Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { viewModel.startSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Suchen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Weitere Aktionen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            if (inboxRoot) {
                                DropdownMenuItem(
                                    text = { Text("Alle als gelesen") },
                                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                                    onClick = { overflowOpen = false; viewModel.markAllRead() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Matrix") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenMatrix() }
                            )
                            DropdownMenuItem(
                                text = { Text("Einstellungen") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { overflowOpen = false; onOpenSettings() }
                            )
                        }
                      }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                // Neue Nachricht verfassen – nur in den Listen-Ansichten, nicht im Detail/Suche/Auswahl.
                if (!selection.active && !state.isSearching && state.conversationFilter == null) {
                    ExtendedFloatingActionButton(
                        onClick = { composeSheetOpen = true },
                        // Voll ausgeschrieben ganz oben, kompakt sobald gescrollt wird.
                        expanded = listState.firstVisibleItemIndex == 0,
                        icon = { Icon(Icons.Default.Edit, contentDescription = "Neue Nachricht") },
                        text = { Text("Neu") }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (!state.hasNotificationAccess) {
                    AccessBanner(onOpenOnboarding)
                }

                // Einmaliger Gesten-Hinweis (nur in den Listen-Ansichten).
                if (state.gestureHintVisible && state.conversationFilter == null &&
                    !state.isSearching && !selection.active
                ) {
                    GestureHintCard(onDismiss = viewModel::dismissGestureHint)
                }

                // Reiter (Ansichten + native Quellen) – nur ausblenden im Detail/Suche.
                if (state.conversationFilter == null && !state.isSearching) {
                    HubFilterBar(
                        selectedTab = state.tab,
                        selectedSourceKey = state.sourceFilter,
                        nativeSources = state.sources.filter { it.isNativeConnector && it.enabled },
                        sourceCounts = state.sourceCounts,
                        pinnedSources = state.pinnedSources,
                        onSelectTab = viewModel::selectTab,
                        onSelectSource = viewModel::selectSourceFilter
                    )
                    // Gruppieren-Umschalter, wo eine gruppierte Übersicht möglich ist.
                    if (state.tab == HubTab.POSTEINGANG || state.sourceFilter != null) {
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
                        listState = listState,
                        onOpen = viewModel::openConversation
                    )
                    return@Column
                }

                MessageList(
                    messages = state.messages,
                    listState = listState,
                    isArchiveView = state.tab == HubTab.ARCHIV && state.sourceFilter == null && state.conversationFilter == null,
                    emptyHint = when {
                        state.conversationFilter != null -> "Keine Nachrichten in dieser Unterhaltung"
                        state.sourceFilter != null -> "Keine Nachrichten dieser App"
                        else -> emptyHintFor(state.tab)
                    },
                    // E-Mail: Tippen/Doppeltippen öffnet den Reader; sonst Antworten/App öffnen.
                    onOpen = { if (isEmail(it)) openEmail(it) else viewModel.openMessage(it) },
                    onReply = { if (isEmail(it)) openEmail(it) else replyMessage = it },
                    onMarkRead = viewModel::markReadUndoable,
                    onArchive = viewModel::archive,
                    onUnarchive = viewModel::unarchive,
                    onOpenPeek = { peekMessage = it },
                    selectionActive = selection.active,
                    selectedIds = selection.ids,
                    onToggleSelect = viewModel::toggleSelected,
                    // Datums-Trenner im Chatverlauf (Konversations-Detail).
                    showDateDividers = state.conversationFilter != null
                )
            }
        }
    }

    peekMessage?.let { message ->
        // Verfügbarkeit der Antwort-Action einmal pro Öffnen prüfen, nicht bei jeder
        // Recomposition - der Zustand kann sich nur zwischen zwei Peeks ändern.
        val canReply = remember(message.id) { viewModel.canReply(message) }
        val conversationMuted = remember(message.id) { viewModel.isConversationMuted(message) }
        val closePeek = {
            peekMessage = null
            viewModel.resetQuickReplyState()
        }

        MessagePeekSheet(
            message = message,
            canQuickReply = canReply,
            isConversationMuted = conversationMuted,
            onToggleConversationMute = {
                viewModel.toggleConversationMuted(message)
                closePeek()
            },
            quickReplyState = quickReplyState,
            onSendQuickReply = { text -> viewModel.sendReply(message, text) },
            onDismiss = closePeek,
            onMarkRead = {
                viewModel.markReadUndoable(message.id)
                closePeek()
            },
            onMarkUnread = {
                viewModel.markUnread(message.id)
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
                if (isEmail(message)) openEmail(message) else viewModel.openMessage(message)
                closePeek()
            },
            onDelete = {
                viewModel.delete(message.id)
                closePeek()
            },
            onSnooze = { duration ->
                viewModel.snooze(message, duration)
                closePeek()
            },
            onSelect = {
                viewModel.enterSelection(message)
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
            },
            canSendVoice = remember(message.id) { viewModel.canSendVoice(message) },
            onSendVoice = { file -> viewModel.sendVoice(message, file) }
        )
    }

    emailMessage?.let { message ->
        EmailReaderSheet(
            message = message,
            onDismiss = { emailMessage = null },
            onArchive = { viewModel.archive(message.id); emailMessage = null },
            onDelete = { viewModel.delete(message.id); emailMessage = null },
            onTogglePriority = { viewModel.setPriority(message.id, !message.priority); emailMessage = null }
        )
    }

    if (composeSheetOpen) {
        val telegramAvailable = state.sources.any {
            it.sourceKey == com.hub.app.connectors.telegram.TelegramBotConnector.SOURCE_KEY && it.enabled
        }
        NewMessageSheet(
            telegramAvailable = telegramAvailable,
            onDismiss = { composeSheetOpen = false },
            onSms = { composeSheetOpen = false; onComposeSms() },
            onMatrix = { composeSheetOpen = false; onOpenMatrix() },
            onTelegram = {
                composeSheetOpen = false
                viewModel.selectSourceFilter(com.hub.app.connectors.telegram.TelegramBotConnector.SOURCE_KEY)
            }
        )
    }
}

/** Auswahl des Kanals für eine neue Nachricht (universeller „Neu"-Einstieg). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMessageSheet(
    telegramAvailable: Boolean,
    onDismiss: () -> Unit,
    onSms: () -> Unit,
    onMatrix: () -> Unit,
    onTelegram: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Neue Nachricht",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("SMS") },
                leadingContent = { Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = onSms)
            )
            ListItem(
                headlineContent = { Text("Matrix-Chat") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = onMatrix)
            )
            if (telegramAvailable) {
                ListItem(
                    headlineContent = { Text("Telegram") },
                    // Bots können keinen Erstkontakt aufbauen – daher: bekannten Chat wählen.
                    supportingContent = { Text("Bestehenden Chat wählen") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable(onClick = onTelegram)
                )
            }
        }
    }
}

/** Einmaliger, wegtippbarer Hinweis auf die Feed-Gesten. */
@Composable
private fun GestureHintCard(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("So geht's schnell", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tippen = Antworten · Doppeltippen = App öffnen · Lang drücken = Menü\n" +
                        "Wischen → als gelesen · Wischen ← archivieren",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Hinweis ausblenden", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationList(
    conversations: List<ConversationSummary>,
    listState: LazyListState,
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
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(conversations, key = { it.sourceKey + "/" + it.groupValue }) { conversation ->
            // Sanftes Verschieben, wenn Unterhaltungen dazukommen/wegfallen (moderne Motion).
            Column(Modifier.animateItemPlacement()) {
                ConversationRow(conversation = conversation, onClick = { onOpen(conversation.ref) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    messages: List<MessageEntity>,
    listState: LazyListState,
    isArchiveView: Boolean,
    emptyHint: String,
    onOpen: (MessageEntity) -> Unit,
    onReply: (MessageEntity) -> Unit,
    onMarkRead: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onOpenPeek: (MessageEntity) -> Unit,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    showDateDividers: Boolean = false
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

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            // Sanftes Verschieben, wenn Zeilen dazukommen/wegfallen (gelesen/archiviert) –
            // moderne Motion nach Material 3.
            Column(Modifier.animateItemPlacement()) {
                // Datums-Trenner (nur im Chatverlauf): über der ersten Nachricht eines Tages.
                if (showDateDividers) {
                    val prev = messages.getOrNull(index - 1)
                    if (prev == null || !isSameDay(prev.timestamp, message.timestamp)) {
                        DateDivider(message.timestamp)
                    }
                }
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
                    onReply = { onReply(message) },
                    selectionActive = selectionActive,
                    selected = message.id in selectedIds,
                    onToggleSelect = { onToggleSelect(message.id) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DateDivider(timestamp: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateLabel(timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun dateLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> "Heute"
        isSameDay(timestamp, now - 24L * 60 * 60 * 1000) -> "Gestern"
        else -> java.text.SimpleDateFormat("EEE, dd.MM.yyyy", java.util.Locale.GERMANY)
            .format(java.util.Date(timestamp))
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

/** E-Mail-Nachricht? (IMAP-Quellen haben den sourceKey-Präfix „imap:".) */
private fun isEmail(message: MessageEntity): Boolean =
    message.sourceKey.startsWith(com.hub.app.connectors.imap.ImapConnector.SOURCE_KEY_PREFIX)

private fun emptyHintFor(tab: HubTab): String = when (tab) {
    HubTab.POSTEINGANG -> "Keine ungelesenen Nachrichten"
    HubTab.PRIORITAET -> "Noch nichts priorisiert.\nHalte eine Nachricht gedrückt, um sie als wichtig zu markieren."
    HubTab.ARCHIV -> "Archiv ist leer"
}
