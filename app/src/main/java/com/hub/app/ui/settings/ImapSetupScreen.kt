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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hub.app.connectors.imap.ImapConfig
import com.hub.app.connectors.imap.ImapConnector
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImapSetupState {
    data class NotConnected(val error: String? = null) : ImapSetupState
    data object Connecting : ImapSetupState
    data class Connected(val account: String) : ImapSetupState
}

class ImapSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val connector: ImapConnector = ServiceLocator.imapConnector(application)
    private val registry = ServiceLocator.connectorRegistry(application)
    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val _state = MutableStateFlow<ImapSetupState>(
        if (connector.isConfigured()) ImapSetupState.Connected("verbunden") else ImapSetupState.NotConnected()
    )
    val state: StateFlow<ImapSetupState> = _state.asStateFlow()

    fun connect(config: ImapConfig) = viewModelScope.launch {
        _state.value = ImapSetupState.Connecting
        connector.signIn(config).fold(
            onSuccess = {
                repository.registerSource(
                    SourceAppEntity(
                        sourceKey = ImapConnector.SOURCE_KEY,
                        label = config.displayName,
                        packageName = null,
                        enabled = true,
                        isNativeConnector = true
                    )
                )
                registry.start(ImapConnector.SOURCE_KEY)
                _state.value = ImapSetupState.Connected(config.username)
            },
            onFailure = { _state.value = ImapSetupState.NotConnected(it.message ?: "Verbindung fehlgeschlagen") }
        )
    }

    fun disconnect() {
        registry.stop(ImapConnector.SOURCE_KEY)
        connector.signOut()
        _state.value = ImapSetupState.NotConnected()
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
                title = { Text("E-Mail (IMAP)") },
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
            Text(
                "Verbindet ein IMAP-Postfach. Zugangsdaten bleiben verschlüsselt auf dem Gerät.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hinweis: Gmail/Outlook brauchen oft ein App-Passwort (kein normales Passwort), " +
                    "und Hub liest neue Mails per Abruf – Ende-zu-Ende-Verschlüsselung von Mail " +
                    "ist nicht Teil davon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(20.dp))

            when (val current = state) {
                is ImapSetupState.Connected -> {
                    Text("Verbunden: ${current.account}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = viewModel::disconnect) { Text("Trennen") }
                }

                ImapSetupState.Connecting -> CircularProgressIndicator()

                is ImapSetupState.NotConnected -> {
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
                        isError = current.error != null,
                        supportingText = current.error?.let { { Text(it) } }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SSL/TLS", modifier = Modifier.weight(1f))
                        Switch(checked = useSsl, onCheckedChange = { useSsl = it })
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.connect(
                                ImapConfig(
                                    displayName = username.substringAfter('@', "").ifBlank { username }.let { "E-Mail ($username)" },
                                    host = host.trim(),
                                    port = port.toIntOrNull() ?: 993,
                                    username = username.trim(),
                                    password = password,
                                    useSsl = useSsl
                                )
                            )
                        },
                        enabled = host.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Testen & verbinden") }
                }
            }
        }
    }
}
