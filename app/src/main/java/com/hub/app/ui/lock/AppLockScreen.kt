package com.hub.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Sperrbildschirm der App. Nutzt [BiometricPrompt] mit
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, damit auch Geräte ohne registrierte Biometrie
 * über PIN/Muster/Passwort entsperren können.
 *
 * Voraussetzung: Die Host-Activity muss eine [FragmentActivity] sein – BiometricPrompt
 * hängt sich intern an den Fragment-Manager (siehe MainActivity).
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current.findFragmentActivity()
    var error by remember { mutableStateOf<String?>(null) }
    var promptVisible by remember { mutableStateOf(false) }

    val showPrompt: () -> Unit = show@{
        if (activity == null || promptVisible) return@show
        promptVisible = true

        val executor: Executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptVisible = false
                    error = null
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptVisible = false
                    // Abbruch durch den Nutzer ist kein Fehler, den man ihm vorwerfen muss.
                    error = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) null else errString.toString()
                }

                override fun onAuthenticationFailed() {
                    // Einzelner Fehlversuch - der Prompt bleibt offen, kein State-Wechsel.
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Hub entsperren")
                .setSubtitle("Deine Nachrichten sind geschützt")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    // Beim ersten Anzeigen direkt fragen, statt den Nutzer erst tippen zu lassen.
    LaunchedEffect(Unit) { showPrompt() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Hub ist gesperrt", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            error ?: "Entsperre mit Biometrie oder deiner Geräte-PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = showPrompt) { Text("Entsperren") }
    }
}

/** BiometricPrompt braucht eine FragmentActivity; Compose gibt uns nur den Context. */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
