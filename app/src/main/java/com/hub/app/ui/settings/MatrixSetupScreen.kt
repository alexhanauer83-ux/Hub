package com.hub.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hub.app.connectors.matrix.MatrixConnector
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MatrixSetupState {
    data class NotConnected(val error: String? = null) : MatrixSetupState
    data object Working : MatrixSetupState
    data class Connected(
        val userId: String,
        val contacts: List<MatrixConnector.MatrixContact> = emptyList(),
        val contactsLoading: Boolean = false
    ) : MatrixSetupState
}

class MatrixSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val connector: MatrixConnector = ServiceLocator.matrixConnector(application)
    private val registry = ServiceLocator.connectorRegistry(application)
    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val _state = MutableStateFlow<MatrixSetupState>(
        if (connector.isConfigured()) {
            MatrixSetupState.Connected(connector.currentUserId() ?: "verbunden")
        } else {
            MatrixSetupState.NotConnected()
        }
    )
    val state: StateFlow<MatrixSetupState> = _state.asStateFlow()

    init {
        if (connector.isConfigured()) loadContacts()
    }

    fun login(homeserver: String, username: String, password: String) = viewModelScope.launch {
        _state.value = MatrixSetupState.Working
        finish(connector.login(homeserver, username, password))
    }

    fun register(homeserver: String, username: String, password: String) = viewModelScope.launch {
        _state.value = MatrixSetupState.Working
        finish(connector.register(homeserver, username, password))
    }

    private suspend fun finish(result: Result<String>) {
        result.fold(
            onSuccess = { userId ->
                repository.registerSource(
                    SourceAppEntity(
                        sourceKey = MatrixConnector.SOURCE_KEY,
                        label = "Matrix",
                        packageName = null,
                        enabled = true,
                        isNativeConnector = true
                    )
                )
                registry.start(MatrixConnector.SOURCE_KEY)
                _state.value = MatrixSetupState.Connected(userId)
                loadContacts()
            },
            onFailure = { error ->
                _state.value = MatrixSetupState.NotConnected(error.message ?: "Fehlgeschlagen")
            }
        )
    }

    fun loadContacts() = viewModelScope.launch {
        val current = _state.value as? MatrixSetupState.Connected ?: return@launch
        _state.value = current.copy(contactsLoading = true)
        val contacts = connector.fetchContacts().getOrDefault(emptyList())
        (_state.value as? MatrixSetupState.Connected)?.let {
            _state.value = it.copy(contacts = contacts, contactsLoading = false)
        }
    }

    fun disconnect() {
        registry.stop(MatrixConnector.SOURCE_KEY)
        connector.signOut()
        _state.value = MatrixSetupState.NotConnected()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatrixSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var homeserver by remember { mutableStateOf("https://matrix.org") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Matrix verbinden") },
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
                "Hub verbindet sich direkt mit deinem Matrix-Homeserver. Nachrichten und " +
                    "Zugangsdaten bleiben lokal verschlüsselt auf dem Gerät.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Hinweis: Ende-zu-Ende-verschlüsselte Räume kann Hub nicht entschlüsseln " +
                    "(kein Krypto-SDK) – dort erscheint ein Platzhalter. Unverschlüsselte " +
                    "Räume funktionieren vollständig.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(24.dp))

            when (val current = state) {
                is MatrixSetupState.Connected -> ConnectedContent(
                    state = current,
                    onReloadContacts = viewModel::loadContacts,
                    onDisconnect = viewModel::disconnect
                )

                MatrixSetupState.Working -> CircularProgressIndicator()

                is MatrixSetupState.NotConnected -> {
                    OutlinedTextField(
                        value = homeserver,
                        onValueChange = { homeserver = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Homeserver") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Benutzername") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = current.error != null,
                        supportingText = current.error?.let { { Text(it) } }
                    )
                    Spacer(Modifier.height(16.dp))
                    val canSubmit = homeserver.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                    Button(
                        onClick = { viewModel.login(homeserver, username, password) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Anmelden")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.register(homeserver, username, password) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Neues Konto anlegen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    state: MatrixSetupState.Connected,
    onReloadContacts: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        Text("Angemeldet als", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.userId, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDisconnect) { Text("Abmelden") }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Kontakte / Chats", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReloadContacts) { Text("Aktualisieren") }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.contactsLoading -> CircularProgressIndicator()
            state.contacts.isEmpty() -> Text(
                "Keine Räume gefunden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> state.contacts.forEach { contact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
