package com.hub.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.hub.app.data.local.entity.SourceAppEntity

/**
 * Linkes Navigations-Menü (per Wisch von links geöffnet). Listet alle aktiven Quellen mit
 * App-Icon und der Anzahl aktiver (ungelesener) Nachrichten. Auswahl einer Quelle zeigt
 * anschließend alle Nachrichten dieser App im Feed.
 */
@Composable
fun SourceDrawer(
    sources: List<SourceAppEntity>,
    sourceCounts: Map<String, Int>,
    selectedSourceKey: String?,
    onSelectSource: (String?) -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = "Quellen",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp)
        )

        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
            // "Posteingang" hebt jeden Quellenfilter auf.
            item(key = "__inbox__") {
                NavigationDrawerItem(
                    label = { Text("Posteingang") },
                    selected = selectedSourceKey == null,
                    icon = { Icon(Icons.Default.AllInbox, contentDescription = null) },
                    badge = {
                        val total = sourceCounts.values.sum()
                        if (total > 0) Text(total.toString(), fontWeight = FontWeight.SemiBold)
                    },
                    onClick = { onSelectSource(null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(Modifier.height(4.dp))
            }

            // Nur aktivierte Quellen; die mit den meisten aktiven Nachrichten zuerst.
            val visible = sources
                .filter { it.enabled }
                .sortedByDescending { sourceCounts[it.sourceKey] ?: 0 }

            items(visible, key = { it.sourceKey }) { source ->
                val count = sourceCounts[source.sourceKey] ?: 0
                NavigationDrawerItem(
                    label = { Text(source.label) },
                    selected = source.sourceKey == selectedSourceKey,
                    icon = { SourceIcon(source) },
                    badge = { if (count > 0) Badge { Text(count.toString()) } },
                    onClick = { onSelectSource(source.sourceKey) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    }
}

@Composable
private fun SourceIcon(source: SourceAppEntity) {
    val icon = rememberAppIcon(source.sourcePackageNameOrNull())
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
        )
    } else {
        // Rückfall: farbiger Punkt (deterministisch je Quelle, wie in den Feed-Zeilen).
        Box(
            Modifier
                .size(20.dp)
                .background(colorForSource(source.sourceKey), CircleShape)
        )
    }
}

/** API-Connectoren (Telegram/SMS) haben keinen Paketnamen; dann greift der Farb-Fallback. */
private fun SourceAppEntity.sourcePackageNameOrNull(): String? = packageName
