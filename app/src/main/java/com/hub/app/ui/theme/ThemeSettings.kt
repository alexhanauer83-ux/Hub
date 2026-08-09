package com.hub.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Darstellungsmodus der App. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persistiert den Theme-Modus (normale SharedPreferences, nichts Sensibles) und stellt ihn
 * als [StateFlow] bereit, damit ein Wechsel in den Einstellungen die App **sofort** neu
 * einfärbt, ohne Neustart.
 */
class ThemeSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
            _mode.value = value
        }

    /**
     * Material You / dynamische Farben: Auf Android 12+ übernimmt die App die Farben des
     * System-Hintergrunds. Standard: an (moderne Optik); Fallback ist die Marken-Palette.
     */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()
            _dynamicColor.value = value
        }

    /** Lädt die gespeicherten Werte in die geteilten Flows (beim App-Start aufrufen). */
    fun sync() {
        _mode.value = mode
        _dynamicColor.value = dynamicColor
    }

    companion object {
        private const val PREFS_NAME = "hub_theme"
        private const val KEY_MODE = "mode"
        private const val KEY_DYNAMIC = "dynamic_color"

        private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
        /** Geteilte Quelle der Wahrheit für die aktuell aktive Darstellung. */
        val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

        private val _dynamicColor = MutableStateFlow(true)
        /** Geteilte Quelle der Wahrheit für Material You (dynamische Farben). */
        val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    }
}
