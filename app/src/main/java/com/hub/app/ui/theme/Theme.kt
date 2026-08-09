package com.hub.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    // Material You: auf Android 12+ die System-/Wallpaper-Farben übernehmen (moderne Optik),
    // sonst die feste Marken-Palette.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDarkTheme -> HubDarkColors
        else -> HubLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HubTypography,
        content = content
    )
}
