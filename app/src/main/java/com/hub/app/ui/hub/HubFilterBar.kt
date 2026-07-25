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

/**
 * Ansichts-Tabs (Posteingang / Priorität / Archiv). Die Auswahl der konkreten Quelle
 * (welche App) läuft nicht mehr hier, sondern über den [SourceDrawer] (Wisch von links).
 */
@Composable
fun HubFilterBar(
    selectedTab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
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
}

private fun HubTab.label(): String = when (this) {
    HubTab.POSTEINGANG -> "Posteingang"
    HubTab.PRIORITAET -> "Priorität"
    HubTab.ARCHIV -> "Archiv"
}
