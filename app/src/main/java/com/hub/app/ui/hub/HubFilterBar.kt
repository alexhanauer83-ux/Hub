package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.SourceAppEntity

/**
 * Reiter-Leiste: Ansichten (Posteingang / Priorität / Archiv) **und** je ein Reiter pro
 * nativ angebundener Quelle (Matrix / Telegram / E-Mail-Konten). Auswahl einer Quelle
 * zeigt deren Chats gruppiert; eine Ansicht hebt den Quellenfilter auf.
 */
@Composable
fun HubFilterBar(
    selectedTab: HubTab,
    selectedSourceKey: String?,
    nativeSources: List<SourceAppEntity>,
    onSelectTab: (HubTab) -> Unit,
    onSelectSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(HubTab.entries.toList(), key = { "tab_${it.name}" }) { tab ->
            FilterChip(
                selected = selectedSourceKey == null && tab == selectedTab,
                onClick = { onSelectTab(tab) },
                label = { Text(tab.label()) }
            )
        }
        items(nativeSources, key = { "src_${it.sourceKey}" }) { source ->
            FilterChip(
                selected = source.sourceKey == selectedSourceKey,
                onClick = { onSelectSource(source.sourceKey) },
                label = { Text(source.label) }
            )
        }
    }
}

private fun HubTab.label(): String = when (this) {
    HubTab.POSTEINGANG -> "Posteingang"
    HubTab.PRIORITAET -> "Priorität"
    HubTab.ARCHIV -> "Archiv"
}
