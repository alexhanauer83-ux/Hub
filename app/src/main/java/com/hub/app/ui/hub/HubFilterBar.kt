package com.hub.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.SourceAppEntity

/**
 * Reiter-Leiste: Ansichten (Posteingang / Priorität / Archiv) **und** je ein Reiter pro
 * nativer Quelle (Matrix / Telegram / E-Mail-Konten) mit Ungelesen-Badge. Angepinnte
 * Quellen stehen vorne. Auswahl einer Quelle zeigt deren Chats gruppiert.
 */
@Composable
fun HubFilterBar(
    selectedTab: HubTab,
    selectedSourceKey: String?,
    nativeSources: List<SourceAppEntity>,
    sourceCounts: Map<String, Int>,
    pinnedSources: Set<String>,
    onSelectTab: (HubTab) -> Unit,
    onSelectSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inboxUnread = sourceCounts.values.sum()
    // Angepinnte Quellen zuerst, sonst Reihenfolge unverändert.
    val orderedSources = nativeSources.sortedByDescending { it.sourceKey in pinnedSources }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(HubTab.entries.toList(), key = { "tab_${it.name}" }) { tab ->
            FilterChip(
                selected = selectedSourceKey == null && tab == selectedTab,
                onClick = { onSelectTab(tab) },
                label = { Text(tab.label()) },
                trailingIcon = countBadge(if (tab == HubTab.POSTEINGANG) inboxUnread else 0)
            )
        }
        items(orderedSources, key = { "src_${it.sourceKey}" }) { source ->
            FilterChip(
                selected = source.sourceKey == selectedSourceKey,
                onClick = { onSelectSource(source.sourceKey) },
                label = {
                    val pin = if (source.sourceKey in pinnedSources) "📌 " else ""
                    Text("$pin${source.label}")
                },
                trailingIcon = countBadge(sourceCounts[source.sourceKey] ?: 0)
            )
        }
    }
}

/** Kleines Ungelesen-Badge als trailingIcon, oder null wenn 0. */
private fun countBadge(count: Int): (@Composable () -> Unit)? =
    if (count <= 0) null else {
        { Badge { Text(if (count > 99) "99+" else count.toString()) } }
    }

private fun HubTab.label(): String = when (this) {
    HubTab.POSTEINGANG -> "Posteingang"
    HubTab.PRIORITAET -> "Priorität"
    HubTab.ARCHIV -> "Archiv"
}
