package com.hub.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Datenschutz-Anforderung aus der Spec: Die Notification-Access-Berechtigung wird **erst
 * nach** einer klaren In-App-Erklärung angefragt, nie durch stillen Sprung in die
 * Systemeinstellungen. Der Screen erklärt konkret, was gelesen wird, wo es bleibt und
 * welche Grenzen es gibt.
 */
@Composable
fun OnboardingScreen(
    onGrantAccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Ein Posteingang für alles", style = MaterialTheme.typography.titleLarge)
        Text(
            "Hub sammelt Nachrichten aus deinen Apps an einem Ort – wie der BlackBerry Hub, " +
                "nur modern. Dafür braucht Hub Zugriff auf deine Benachrichtigungen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        ExplanationCard(
            icon = Icons.Default.Notifications,
            title = "Was Hub liest",
            body = "Absender, Text, Quell-App und Zeitstempel eingehender Benachrichtigungen. " +
                "Du kannst in den Einstellungen jederzeit einzelne Apps ausschließen."
        )
        ExplanationCard(
            icon = Icons.Default.Lock,
            title = "Wo die Daten bleiben",
            body = "Ausschließlich auf diesem Gerät, in einer verschlüsselten lokalen Datenbank. " +
                "Keine Cloud, keine Synchronisierung, kein Analytics, kein Tracking."
        )
        ExplanationCard(
            icon = Icons.Default.VisibilityOff,
            title = "Eine bekannte Grenze",
            body = "Wenn Android sensible Inhalte auf dem Sperrbildschirm ausblendet, liefern manche " +
                "Apps nur „Neue Nachricht“ statt des Textes. Hub markiert solche Einträge, statt sie " +
                "als vollständig auszugeben."
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onGrantAccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zugriff in den Einstellungen erlauben")
        }
        Text(
            "Der nächste Schritt öffnet die Android-Einstellungen. Dort „Hub“ auswählen und aktivieren.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Später entscheiden")
        }
    }
}

@Composable
private fun ExplanationCard(icon: ImageVector, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
