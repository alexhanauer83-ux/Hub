package com.hub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HubDarkColors = darkColorScheme(
    primary = HubAccent,
    onPrimary = HubBackground,
    secondary = HubAccentMuted,
    background = HubBackground,
    surface = HubSurface,
    surfaceVariant = HubSurfaceVariant,
    onBackground = HubOnSurface,
    onSurface = HubOnSurface,
    onSurfaceVariant = HubOnSurfaceMuted,
    error = HubDanger,
    outline = HubOutline
)

private val HubLightColors = lightColorScheme(
    primary = HubAccent,
    onPrimary = HubLightOnSurface,
    background = HubLightBackground,
    surface = HubLightSurface,
    onBackground = HubLightOnSurface,
    onSurface = HubLightOnSurface,
    error = HubDanger
)

/**
 * Dunkles Theme ist Standard (siehe Spec: "dunkles Theme als Standard"), unabhängig vom
 * System-Theme – nur wenn der Nutzer künftig explizit "System folgen" wählt, greift
 * [isSystemInDarkTheme]. Für den MVP fest auf dark, mit optionalem useDarkTheme-Override
 * für spätere Einstellungen.
 */
@Composable
fun HubTheme(
    useDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) HubDarkColors else HubLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = HubTypography,
        content = content
    )
}
