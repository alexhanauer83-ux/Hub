package com.hub.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hub.app.connectors.ConnectorSyncService
import com.hub.app.connectors.imap.ImapConfig
import com.hub.app.connectors.imap.ImapConnector
import com.hub.app.connectors.imap.ImapCredentialStore
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImapUiState(
    val accounts: List<ImapConfig> = emptyList(),
    val adding: Boolean = false,
    val error: String? = null
)

class ImapSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val credentials = ImapCredentialStore(application)
    private val registry = ServiceLocator.connectorRegistry(application)
    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val _state = MutableStateFlow(ImapUiState(accounts = credentials.loadAll()))
    val state: StateFlow<ImapUiState> = _state.asStateFlow()

    private fun reload() {
        _state.value = _state.value.copy(accounts = credentials.loadAll())
    }

    fun addAccount(config: ImapConfig) = viewModelScope.launch {
        _state.value = _state.value.copy(adding = true, error = null)
        val app = getApplication<Application>()
        val connector = ServiceLocator.imapConnector(app, config.accountId)
        connector.signIn(config).fold(
            onSuccess = {
                repository.registerSource(
                    SourceAppEntity(
                        sourceKey = ImapConnector.sourceKeyFor(config.accountId),
                        label = config.displayName,
                        packageName = null,
                        enabled = true,
                        isNativeConnector = true
                    )
                )
                registry.start(ImapConnector.sourceKeyFor(config.accountId))
                ConnectorSyncService.startIfEnabled(app)
                _state.value = _state.value.copy(adding = false, error = null)
                reload()
            },
            onFailure = { _state.value = _state.value.copy(adding = false, error = it.message ?: "Verbindung fehlgeschlagen") }
        )
    }

    fun removeAccount(accountId: String) = viewModelScope.launch {
        val app = getApplication<Application>()
        registry.stop(ImapConnector.sourceKeyFor(accountId))
        ServiceLocator.imapConnector(app, accountId).signOut()
        if (!ConnectorSyncService.anyConnectorConfigured(app)) ConnectorSyncService.stop(app)
        reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImapSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImapSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("993") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("E-Mail-Konten (IMAP)") },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Verbundene Konten ---
            if (state.accounts.isNotEmpty()) {
                Text("Verbundene Konten", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.accounts.forEach { account ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.username, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                account.host,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeAccount(account.accountId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Entfernen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
            }

            // --- Neues Konto ---
            Text("Neues Konto hinzufügen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Gmail/Outlook brauchen oft ein App-Passwort. Hub ruft neue Mails ab; " +
                    "Ende-zu-Ende-Verschlüsselung von Mail ist nicht Teil davon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))

            if (state.adding) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("IMAP-Server (z. B. imap.gmail.com)") }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Port") }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    label = { Text("E-Mail-Adresse / Benutzer") }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Passwort / App-Passwort") },
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } }
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SSL/TLS", modifier = Modifier.weight(1f))
                    Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val user = username.trim()
                        viewModel.addAccount(
                            ImapConfig(
                                accountId = user.lowercase(),
                                displayName = "E-Mail ($user)",
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 993,
                                username = user,
                                password = password,
                                useSsl = useSsl
                            )
                        )
                        host = ""; port = "993"; username = ""; password = ""; useSsl = true
                    },
                    enabled = host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Testen & hinzufügen") }
            }
        }
    }
}
