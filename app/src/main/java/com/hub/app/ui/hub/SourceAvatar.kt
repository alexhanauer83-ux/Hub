package com.hub.app.ui.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Runder Avatar für eine Zeile: das App-Icon der Quell-App (Notification-Quellen), sonst
 * ein Kreis in der Quellenfarbe mit der Initiale des Namens (Matrix/Telegram/SMS/E-Mail,
 * die keinen Paketnamen haben).
 */
@Composable
fun SourceAvatar(
    sourceKey: String,
    packageName: String?,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val icon = rememberAppIcon(packageName)
    // Farbe pro Kontakt (aus dem Namen abgeleitet) statt einheitlicher Quellenfarbe – so
    // unterscheiden sich Chats/Absender auf einen Blick, wie in modernen Messengern.
    val circleColor = colorForSource(title.ifBlank { sourceKey })
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (icon == null) circleColor else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initialsOf(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // Dunkle Schrift auf dem pastellfarbenen Kreis – in beiden Themes lesbar.
                color = Color(0xFF14161B)
            )
        }
    }
}

/** Bis zu zwei Initialen: Anfangsbuchstaben von erstem und letztem Wort des Namens. */
private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "•"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
