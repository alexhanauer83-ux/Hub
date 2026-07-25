package com.hub.app.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hub.app.data.local.entity.SourceAppEntity

/**
 * Zwei Filterebenen, bewusst getrennt gehalten:
 *  - obere Reihe = Ansicht (Alle / Ungelesen / Priorität / Archiv)
 *  - untere Reihe = Quellenfilter (welche App), unabhängig von der Ansicht
 */
@Composable
fun HubFilterBar(
    selectedTab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    sources: List<SourceAppEntity>,
    selectedSourceKey: String?,
    onSelectSource: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(HubTab.entries.toList(), key = { it.name }) { tab ->
                FilterChip(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(tab.label()) }
                )
            }
        }

        if (sources.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item(key = "__all__") {
                    FilterChip(
                        selected = selectedSourceKey == null,
                        onClick = { onSelectSource(null) },
                        label = { Text("Alle Quellen") }
                    )
                }
                items(sources.filter { it.enabled }, key = { it.sourceKey }) { source ->
                    FilterChip(
                        selected = source.sourceKey == selectedSourceKey,
                        onClick = {
                            // Erneuter Tipp auf die aktive Quelle hebt den Filter auf.
                            onSelectSource(if (source.sourceKey == selectedSourceKey) null else source.sourceKey)
                        },
                        label = { Text(source.label) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(colorForSource(source.sourceKey), CircleShape)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

private fun HubTab.label(): String = when (this) {
    HubTab.ALLE -> "Alle"
    HubTab.UNGELESEN -> "Ungelesen"
    HubTab.PRIORITAET -> "Priorität"
    HubTab.ARCHIV -> "Archiv"
}
