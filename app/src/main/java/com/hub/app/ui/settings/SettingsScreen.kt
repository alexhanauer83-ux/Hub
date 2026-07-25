package com.hub.app.ui.settings

import android.Manifest
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                SectionHeader("Sicherheit")
                SecuritySection(
                    isAppLockEnabled = state.isAppLockEnabled,
                    canUseAppLock = state.canUseAppLock,
                    onToggleAppLock = viewModel::setAppLockEnabled
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SectionHeader("Quellen")
            }

            items(state.sources, key = { it.sourceKey }) { source ->
                SourceRow(
                    source = source,
                    onToggleEnabled = { viewModel.setSourceEnabled(source.sourceKey, it) },
                    onTogglePriority = { viewModel.setSourcePriority(source.sourceKey, !source.isPriority) }
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
        }
    }
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
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePriority: () -> Unit
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
                if (source.isNativeConnector) "Direkte API-Anbindung" else "Über Benachrichtigungen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
