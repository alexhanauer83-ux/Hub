package com.hub.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hub.app.data.local.entity.SourceAppEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTelegramSetup: () -> Unit,
    onOpenMatrixSetup: () -> Unit,
    onOpenImapSetup: () -> Unit,
    onOpenComposeSms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val themeMode by com.hub.app.ui.theme.ThemeSettings.mode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Beim Öffnen der Einstellungen einmalig automatisch nach einem Update suchen.
    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    // Rollen-Dialog und Runtime-Permission liefern ihr Ergebnis asynchron zurueck;
    // danach den Systemzustand neu einlesen.
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshSystemState() }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.refreshSystemState()
        if (granted[Manifest.permission.READ_SMS] == true) viewModel.importSmsHistory()
    }

    // POST_NOTIFICATIONS (Android 13+) wird gebraucht, damit Hub eigene Benachrichtigungen
    // anzeigen kann - Voraussetzung fuer "nur Hub anzeigen".
    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setReplaceOtherNotifications(granted) }

    // Klingelton-Auswahl pro Quelle: merkt sich die Quelle, deren Ergebnis async zurückkommt.
    var soundPickSource by remember { mutableStateOf<String?>(null) }
    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        soundPickSource?.let { viewModel.setSourceSound(it, uri?.toString()) }
        soundPickSource = null
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionHeader("Darstellung")
                ThemeSection(
                    current = themeMode,
                    onSelect = { com.hub.app.ui.theme.ThemeSettings(context).mode = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SectionHeader("Sicherheit")
                SecuritySection(
                    isAppLockEnabled = state.isAppLockEnabled,
                    canUseAppLock = state.canUseAppLock,
                    onToggleAppLock = viewModel::setAppLockEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SectionHeader("Benachrichtigungen")
                NotificationReplacementSection(
                    enabled = state.replaceOtherNotifications,
                    onToggle = { wantEnabled ->
                        if (wantEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setReplaceOtherNotifications(wantEnabled)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SectionHeader("Ruhezeiten")
                QuietHoursSection(
                    enabled = state.quietHoursEnabled,
                    startMinutes = state.quietHoursStart,
                    endMinutes = state.quietHoursEnd,
                    onToggle = viewModel::setQuietHoursEnabled,
                    onPickStart = {
                        showTimePicker(context, state.quietHoursStart) { viewModel.setQuietHoursStart(it) }
                    },
                    onPickEnd = {
                        showTimePicker(context, state.quietHoursEnd) { viewModel.setQuietHoursEnd(it) }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SectionHeader("Direkte Anbindungen")
                Text(
                    "Über die offene API angebundene Dienste liefern den vollen Verlauf und " +
                        "sind zuverlässiger als der Weg über Benachrichtigungen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                TextButton(
                    onClick = onOpenTelegramSetup,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Telegram einrichten")
                }
                TextButton(
                    onClick = onOpenMatrixSetup,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Matrix einrichten")
                }
                TextButton(
                    onClick = onOpenImapSetup,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("E-Mail (IMAP) einrichten")
                }
                if (state.hasConnector) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Hintergrund-Empfang", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connectoren auch bei geschlossener App abfragen (zeigt eine " +
                                    "dauerhafte, leise Hinweis-Benachrichtigung).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.backgroundSyncEnabled,
                            onCheckedChange = viewModel::setBackgroundSyncEnabled
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                SectionHeader("SMS")
                SmsSection(
                    isDefaultSmsApp = state.isDefaultSmsApp,
                    hasReadPermission = state.hasSmsReadPermission,
                    onRequestRole = {
                        viewModel.smsRoleRequestIntent()?.let { roleLauncher.launch(it) }
                    },
                    onOpenRoleSettings = {
                        context.startActivity(viewModel.smsRoleSettingsIntent())
                    },
                    onRequestPermissions = {
                        smsPermissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
                        )
                    },
                    onImportHistory = viewModel::importSmsHistory
                )
                TextButton(
                    onClick = onOpenComposeSms,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("SMS schreiben (mit Kontakten)")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SectionHeader("Quellen")
            }

            items(state.sources, key = { it.sourceKey }) { source ->
                SourceRow(
                    source = source,
                    muted = source.sourceKey in state.mutedSources,
                    hasCustomSound = viewModel.currentSourceSound(source.sourceKey) != null,
                    onToggleEnabled = { viewModel.setSourceEnabled(source.sourceKey, it) },
                    onTogglePriority = { viewModel.setSourcePriority(source.sourceKey, !source.isPriority) },
                    onToggleMuted = { viewModel.setSourceMuted(source.sourceKey, it) },
                    onChooseSound = {
                        soundPickSource = source.sourceKey
                        ringtonePicker.launch(
                            buildRingtonePickerIntent(viewModel.currentSourceSound(source.sourceKey))
                        )
                    }
                )
            }

            if (state.sources.isEmpty()) {
                item {
                    Text(
                        "Noch keine Quellen erkannt. Sobald Apps Benachrichtigungen senden, " +
                            "erscheinen sie hier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SectionHeader("App-Update")
                UpdateSection(
                    currentVersion = viewModel.currentVersion,
                    state = updateState,
                    onCheck = viewModel::checkForUpdate,
                    onInstall = { info -> viewModel.downloadAndInstall(info) },
                    onGrantInstallPermission = {
                        context.startActivity(viewModel.installPermissionIntent())
                    },
                    onRetryInstall = viewModel::installPending
                )
            }
        }
    }
}

@Composable
private fun UpdateSection(
    currentVersion: String,
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: (com.hub.app.update.UpdateManager.UpdateInfo) -> Unit,
    onGrantInstallPermission: () -> Unit,
    onRetryInstall: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Installierte Version: $currentVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            UpdateState.Checking -> Text("Suche nach Updates …", style = MaterialTheme.typography.bodyMedium)
            UpdateState.Downloading -> Text(
                "Update wird im Hintergrund geladen – du kannst die App verlassen. " +
                    "Sobald es fertig ist, tippe die Benachrichtigung „Update bereit“ " +
                    "oder öffne Hub erneut, dann startet die Installation.",
                style = MaterialTheme.typography.bodyMedium
            )
            UpdateState.UpToDate -> {
                Text("Du hast die neueste Version.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onCheck) { Text("Erneut prüfen") }
            }
            is UpdateState.Available -> {
                Text(
                    "Update verfügbar: Version ${s.info.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                s.info.notes?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onInstall(s.info) }) { Text("Jetzt aktualisieren") }
            }
            is UpdateState.NeedsInstallPermission -> {
                Text(
                    "Fast fertig: Erlaube Hub das Installieren von Apps, dann kann das " +
                        "Update abgeschlossen werden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGrantInstallPermission) { Text("Berechtigung erteilen") }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRetryInstall) { Text("Danach: Installieren" ) }
            }
            is UpdateState.Error -> {
                Text("Prüfung fehlgeschlagen: ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onCheck) { Text("Erneut prüfen") }
            }
            UpdateState.Idle -> TextButton(onClick = onCheck) { Text("Nach Updates suchen") }
        }
    }
}

@Composable
private fun NotificationReplacementSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nur Hub in der Statusleiste", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Hub entfernt eingehende Benachrichtigungen anderer Apps aus der " +
                        "Statusleiste und zeigt stattdessen eine eigene.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Hinweis: Der Ton der Original-App ertönt einmal, bevor Hub die " +
                    "Benachrichtigung entfernt. Direktantwort (z. B. WhatsApp) funktioniert " +
                    "danach nur noch eingeschränkt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun QuietHoursSection(
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int,
    onToggle: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nachts nicht stören", style = MaterialTheme.typography.titleMedium)
                Text(
                    "In diesem Zeitfenster stellt Hub Nachrichten leise zu (kein Ton, kein " +
                        "Heads-up) – sie erscheinen weiter im Feed. Wirkt in Hubs eigenen " +
                        "Benachrichtigungen („Nur Hub in der Statusleiste“).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickStart) { Text("Von ${formatMinutes(startMinutes)}") }
                TextButton(onClick = onPickEnd) { Text("Bis ${formatMinutes(endMinutes)}") }
            }
        }
    }
}

/** Öffnet den System-Zeitwähler und meldet die gewählte Zeit als Minuten seit Mitternacht. */
private fun showTimePicker(context: Context, minutes: Int, onSet: (Int) -> Unit) {
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onSet(hour * 60 + minute) },
        minutes / 60,
        minutes % 60,
        true
    ).show()
}

private fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun ThemeSection(
    current: com.hub.app.ui.theme.ThemeMode,
    onSelect: (com.hub.app.ui.theme.ThemeMode) -> Unit
) {
    val options = listOf(
        com.hub.app.ui.theme.ThemeMode.SYSTEM to "System",
        com.hub.app.ui.theme.ThemeMode.LIGHT to "Hell",
        com.hub.app.ui.theme.ThemeMode.DARK to "Dunkel"
    )
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            androidx.compose.material3.FilterChip(
                selected = current == mode,
                onClick = { onSelect(mode) },
                label = { Text(label) }
            )
        }
    }
}

/** System-Dialog zur Auswahl eines Benachrichtigungstons, mit aktueller Vorauswahl. */
private fun buildRingtonePickerIntent(currentUri: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ton wählen")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            currentUri?.let { Uri.parse(it) }
        )
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SecuritySection(
    isAppLockEnabled: Boolean,
    canUseAppLock: Boolean,
    onToggleAppLock: (Boolean) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App-Sperre", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (canUseAppLock) {
                        "Beim Öffnen per Biometrie oder Geräte-PIN entsperren."
                    } else {
                        "Auf diesem Gerät ist keine Bildschirmsperre eingerichtet."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAppLockEnabled,
                onCheckedChange = onToggleAppLock,
                enabled = canUseAppLock
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Alle Nachrichten bleiben verschlüsselt auf diesem Gerät. Keine Cloud, keine " +
                "Synchronisierung, kein Tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmsSection(
    isDefaultSmsApp: Boolean,
    hasReadPermission: Boolean,
    onRequestRole: () -> Unit,
    onOpenRoleSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onImportHistory: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Als Standard-SMS-App kann Hub den vollständigen SMS-Verlauf lesen statt nur " +
                "die Benachrichtigungs-Schnipsel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Achtung: Die Standard-SMS-App ist auch für den Empfang zuständig. Hub ist " +
                "primär ein Aggregator und schreibt eingehende SMS derzeit nicht zurück in " +
                "die System-Datenbank – diese Funktion ist experimentell.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))

        if (isDefaultSmsApp) {
            Text("Hub ist aktuell Standard-SMS-App.", style = MaterialTheme.typography.bodyMedium)
            Row {
                if (hasReadPermission) {
                    TextButton(onClick = onImportHistory) { Text("Verlauf importieren") }
                } else {
                    TextButton(onClick = onRequestPermissions) { Text("SMS-Berechtigung erteilen") }
                }
                TextButton(onClick = onOpenRoleSettings) { Text("Rolle abgeben") }
            }
        } else {
            TextButton(onClick = onRequestRole) { Text("Hub als Standard-SMS-App festlegen") }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceAppEntity,
    muted: Boolean,
    hasCustomSound: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePriority: () -> Unit,
    onToggleMuted: (Boolean) -> Unit,
    onChooseSound: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.label, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(if (source.isNativeConnector) "Direkte API-Anbindung" else "Über Benachrichtigungen")
                    if (hasCustomSound) append(" · eigener Ton")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Eigenen Benachrichtigungston für diese Quelle wählen (greift in Hubs eigenen
        // Benachrichtigungen, siehe "Nur Hub in der Statusleiste").
        IconButton(onClick = onChooseSound) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Ton wählen",
                tint = if (hasCustomSound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Stummschalten: keine Hub-Benachrichtigung/kein Ton für diese Quelle.
        IconButton(onClick = { onToggleMuted(!muted) }) {
            Icon(
                imageVector = if (muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                contentDescription = if (muted) "Stumm (tippen zum Aktivieren)" else "Stummschalten",
                tint = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onTogglePriority) {
            Icon(
                imageVector = if (source.isPriority) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Quelle priorisieren",
                tint = if (source.isPriority) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Switch(checked = source.enabled, onCheckedChange = onToggleEnabled)
    }
}
