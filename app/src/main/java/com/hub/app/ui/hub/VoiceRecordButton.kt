package com.hub.app.ui.hub

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Mikrofon-Knopf: erster Tipp startet die Aufnahme (fragt bei Bedarf RECORD_AUDIO an),
 * zweiter Tipp beendet sie und übergibt die Datei via [onRecorded]. Nur bei Matrix nutzbar.
 */
@Composable
fun VoiceRecordButton(
    onRecorded: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && recorder.start()) recording = true
    }

    IconButton(
        modifier = modifier,
        onClick = {
            if (recording) {
                recorder.stop()?.let(onRecorded)
                recording = false
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                if (recorder.start()) recording = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    ) {
        Icon(
            imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (recording) "Aufnahme beenden & senden" else "Sprachnachricht aufnehmen",
            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }

    // Wird das Fenster geschlossen, während aufgenommen wird: sauber abbrechen.
    DisposableEffect(Unit) {
        onDispose { if (recording) recorder.cancel() }
    }
}
