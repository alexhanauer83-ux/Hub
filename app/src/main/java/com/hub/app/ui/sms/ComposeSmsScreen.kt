package com.hub.app.ui.sms

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.hub.app.di.ServiceLocator
import com.hub.app.sms.ContactsRepository
import com.hub.app.sms.PhoneContact
import com.hub.app.sms.SmsMessageSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComposeSmsUiState(
    val hasContactsPermission: Boolean = false,
    val hasSendPermission: Boolean = false,
    val contacts: List<PhoneContact> = emptyList(),
    val query: String = "",
    val status: String? = null
) {
    val filteredContacts: List<PhoneContact>
        get() = if (query.isBlank()) contacts
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.number.contains(query)
        }
}

class ComposeSmsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepo = ContactsRepository(application)
    private val smsSource = SmsMessageSource(application)

    private val _state = MutableStateFlow(readPermissions())
    val state: StateFlow<ComposeSmsUiState> = _state.asStateFlow()

    init {
        if (contactsRepo.hasPermission()) loadContacts()
    }

    private fun readPermissions() = ComposeSmsUiState(
        hasContactsPermission = contactsRepo.hasPermission(),
        hasSendPermission = smsSource.hasSendPermission()
    )

    fun refreshPermissions() {
        _state.value = _state.value.copy(
            hasContactsPermission = contactsRepo.hasPermission(),
            hasSendPermission = smsSource.hasSendPermission()
        )
        if (contactsRepo.hasPermission() && _state.value.contacts.isEmpty()) loadContacts()
    }

    fun setQuery(query: String) { _state.value = _state.value.copy(query = query) }

    private fun loadContacts() = viewModelScope.launch {
        val contacts = contactsRepo.loadContacts()
        _state.value = _state.value.copy(contacts = contacts)
    }

    fun send(number: String, text: String) = viewModelScope.launch {
        val status = smsSource.sendSms(number, text).fold(
            onSuccess = { "SMS an $number gesendet" },
            onFailure = { it.message ?: "Senden fehlgeschlagen" }
        )
        _state.value = _state.value.copy(status = status)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSmsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComposeSmsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var recipient by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("SMS schreiben") },
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
                .padding(16.dp)
        ) {
            if (!state.hasSendPermission || !state.hasContactsPermission) {
                Text(
                    "Zum Senden braucht Hub die SMS-Berechtigung, für die Empfängerauswahl " +
                        "den Kontaktzugriff.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
                    )
                }) { Text("Berechtigungen erteilen") }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Empfänger (Nummer)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nachricht") },
                maxLines = 4
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.send(recipient, message)
                    message = ""
                },
                enabled = recipient.isNotBlank() && message.isNotBlank() && state.hasSendPermission,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Senden") }

            state.status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))

            Text("Kontakte", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (!state.hasContactsPermission) {
                Text(
                    "Kein Kontaktzugriff – du kannst die Nummer oben manuell eingeben.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kontakt suchen") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.filteredContacts, key = { it.number }) { contact ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            TextButton(onClick = { recipient = contact.number }) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        contact.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
