package com.hub.app.ui.hub

import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val swipeConfig by viewModel.swipeConfig.collectAsStateWithLifecycle()
    // Nach Rückkehr aus den Einstellungen die konfigurierten Wisch-Aktionen neu einlesen.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshSwipeConfig()
        onPauseOrDispose { }
    }
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
                        // „Alle als gelesen" in JEDER Kategorie, sobald es Ungelesene gibt
                        // (Posteingang, Quelle/Reiter, Priorität, Konversation) – nicht im Archiv.
                        val hasUnread = state.tab != HubTab.ARCHIV && state.messages.any { !it.isRead }

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
                            if (hasUnread) {
                                DropdownMenuItem(
                                    text = { Text("Alle als gelesen") },
                                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                                    onClick = { overflowOpen = false; viewModel.markVisibleRead() }
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

                // Letzte Suchen anbieten, solange das Suchfeld leer ist.
                if (state.isSearching && state.searchQuery.isNullOrBlank() &&
                    state.recentSearches.isNotEmpty()
                ) {
                    RecentSearches(
                        recent = state.recentSearches,
                        onPick = viewModel::setSearchQuery,
                        onClear = viewModel::clearRecentSearches
                    )
                }

                // Reiter (Ansichten + native Quellen) – nur ausblenden im Detail/Suche.
                if (state.conversationFilter == null && !state.isSearching) {
                    HubFilterBar(
                        selectedTab = state.tab,
                        selectedSourceKey = state.sourceFilter,
                        nativeSources = state.sources.filter { it.isNativeConnector && it.enabled },
                        sourceCounts = state.sourceCounts,
                        pinnedSources = state.pinnedSources,
                        // Re-Tap des aktiven Reiters/Quelle → sanft nach oben scrollen (modernes
                        // Muster wie in Gmail/Chrome), sonst normal umschalten.
                        onSelectTab = { tab ->
                            if (state.sourceFilter == null && tab == state.tab) {
                                scope.launch { listState.animateScrollToItem(0) }
                            } else {
                                viewModel.selectTab(tab)
                            }
                        },
                        onSelectSource = { key ->
                            if (key == state.sourceFilter) {
                                scope.launch { listState.animateScrollToItem(0) }
                            } else {
                                viewModel.selectSourceFilter(key)
                            }
                        }
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
                        onOpen = viewModel::openConversation,
                        onTogglePin = { viewModel.toggleConversationPin(it.sourceKey, it.groupValue) }
                    )
                    return@Column
                }

                MessageList(
                    messages = state.messages,
                    listState = listState,
                    isArchiveView = state.tab == HubTab.ARCHIV && state.sourceFilter == null && state.conversationFilter == null,
                    emptyHint = when {
                        state.isSearching -> "Nichts gefunden"
                        state.conversationFilter != null -> "Keine Nachrichten in dieser Unterhaltung"
                        state.sourceFilter != null -> "Keine Nachrichten dieser App"
                        else -> emptyHintFor(state.tab)
                    },
                    emptyIcon = when {
                        state.isSearching -> Icons.Default.Search
                        state.conversationFilter != null || state.sourceFilter != null ->
                            Icons.AutoMirrored.Filled.Chat
                        state.tab == HubTab.PRIORITAET -> Icons.Default.Star
                        state.tab == HubTab.ARCHIV -> Icons.Default.Archive
                        else -> Icons.Default.Inbox
                    },
                    // E-Mail: Tippen/Doppeltippen öffnet den Reader; sonst Antworten/App öffnen.
                    onOpen = { if (isEmail(it)) openEmail(it) else viewModel.openMessage(it) },
                    onReply = { if (isEmail(it)) openEmail(it) else replyMessage = it },
                    onMarkRead = viewModel::markReadUndoable,
                    onArchive = viewModel::archive,
                    onUnarchive = viewModel::unarchive,
                    onDelete = viewModel::delete,
                    rightAction = swipeConfig.right,
                    leftAction = swipeConfig.left,
                    onOpenPeek = { peekMessage = it },
                    selectionActive = selection.active,
                    selectedIds = selection.ids,
                    onToggleSelect = viewModel::toggleSelected,
                    // Datums-Trenner (Heute/Gestern/Datum) in allen flachen Listen – bessere
                    // Orientierung im Posteingang, in Priorität/Archiv, Suche und im Chatverlauf.
                    showDateDividers = true
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
