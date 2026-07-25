package com.hub.app.ui.hub

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Schlanker Audio-Player für einen lokal gespeicherten Audioanhang (siehe
 * [com.hub.app.notification.AttachmentStore]). Bewusst minimal: Abspielen/Stoppen plus
 * Fortschrittsbalken. Der [MediaPlayer] wird beim Verlassen der Komposition freigegeben.
 */
@Composable
fun AudioPlayer(audioUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val player = remember(audioUri) {
        MediaPlayer().apply {
            runCatching {
                setDataSource(context, Uri.parse(audioUri))
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
            progress = 0f
        }
        onDispose { runCatching { player.release() } }
    }

    // Fortschritt aktualisieren, solange abgespielt wird.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val duration = player.duration.takeIf { it > 0 } ?: 1
            progress = player.currentPosition.toFloat() / duration
            delay(200)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                runCatching { player.pause() }
                isPlaying = false
            } else {
                runCatching { player.start() }
                isPlaying = true
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stopp" else "Abspielen",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text("Audio", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
    }
}
