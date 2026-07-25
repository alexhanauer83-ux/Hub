package com.hub.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
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
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TelegramSetupState {
    data class NotConnected(val error: String? = null) : TelegramSetupState
    data object Connecting : TelegramSetupState
    data class Connected(val botName: String) : TelegramSetupState
}

class TelegramSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val connector: TelegramBotConnector = ServiceLocator.telegramConnector(application)
    private val registry = ServiceLocator.connectorRegistry(application)
    private val repository: MessageRepository = ServiceLocator.messageRepository(application)

    private val _state = MutableStateFlow<TelegramSetupState>(
        if (connector.isConfigured()) TelegramSetupState.Connected("verbunden")
        else TelegramSetupState.NotConnected()
    )
    val state: StateFlow<TelegramSetupState> = _state.asStateFlow()

    fun connect(token: String) = viewModelScope.launch {
        _state.value = TelegramSetupState.Connecting
        connector.signIn(token.trim()).fold(
            onSuccess = { botName ->
                repository.registerSource(
                    SourceAppEntity(
                        sourceKey = TelegramBotConnector.SOURCE_KEY,
                        label = "Telegram",
                        packageName = null,
                        enabled = true,
                        isNativeConnector = true
                    )
                )
                registry.start(TelegramBotConnector.SOURCE_KEY)
                _state.value = TelegramSetupState.Connected(botName)
            },
            onFailure = { error ->
                _state.value = TelegramSetupState.NotConnected(
                    error.message ?: "Verbindung fehlgeschlagen"
                )
            }
        )
    }

    fun disconnect() {
        registry.stop(TelegramBotConnector.SOURCE_KEY)
        connector.signOut()
        _state.value = TelegramSetupState.NotConnected()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TelegramSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Telegram verbinden") },
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
                "Hub verbindet sich über die Telegram Bot API. Erstelle in Telegram bei " +
                    "@BotFather einen Bot und füge das Token hier ein.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Wichtig: Ein Bot sieht nur Nachrichten, die an ihn selbst gerichtet sind – " +
                    "nicht deine privaten Chats. Für die bleibt es beim Weg über " +
                    "Benachrichtigungen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(24.dp))

            when (val current = state) {
                is TelegramSetupState.Connected -> {
                    Text("Verbunden mit ${current.botName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = viewModel::disconnect) { Text("Verbindung trennen") }
                }

                TelegramSetupState.Connecting -> CircularProgressIndicator()

                is TelegramSetupState.NotConnected -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bot-Token") },
                        singleLine = true,
                        // Token = Vollzugriff auf den Bot, daher nicht im Klartext anzeigen.
                        visualTransformation = PasswordVisualTransformation(),
                        isError = current.error != null,
                        supportingText = current.error?.let { { Text(it) } }
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.connect(token) },
                        enabled = token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verbinden")
                    }
                }
            }
        }
    }
}
