package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Platzhalter für Phase 1 (Grundgerüst/Navigation). Die eigentliche Feed-Liste
 * (Room-gestützt, mit Notification-Daten) kommt in Phase 2, Swipe-Gesten/Filter/
 * Priority-Hub-Kachel in Phase 3.
 */
@Composable
fun HubScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Hub") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Noch keine Nachrichten – Grundgerüst steht.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
