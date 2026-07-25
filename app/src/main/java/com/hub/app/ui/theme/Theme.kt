package com.hub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    primary = HubLightAccent,
    onPrimary = Color.White,
    secondary = HubLightAccent,
    background = HubLightBackground,
    surface = HubLightSurface,
    surfaceVariant = HubLightSurfaceVariant,
    onBackground = HubLightOnSurface,
    onSurface = HubLightOnSurface,
    onSurfaceVariant = HubLightOnSurfaceMuted,
    error = HubDanger,
    outline = HubLightOutline
)

/**
 * Theme folgt dem gewählten [ThemeMode]: SYSTEM richtet sich nach der System-Einstellung,
 * LIGHT/DARK erzwingen die jeweilige Darstellung. Umschalten wirkt sofort (der Aufrufer
 * beobachtet [ThemeSettings.mode]).
 */
@Composable
fun HubTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) HubDarkColors else HubLightColors,
        typography = HubTypography,
        content = content
    )
}
