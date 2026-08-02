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
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (icon == null) colorForSource(sourceKey) else Color.Transparent),
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
                text = title.trim().firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // Dunkle Schrift auf dem pastellfarbenen Kreis – in beiden Themes lesbar.
                color = Color(0xFF14161B)
            )
        }
    }
}
