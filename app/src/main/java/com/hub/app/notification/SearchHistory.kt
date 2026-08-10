package com.hub.app.notification

import android.content.Context

/**
 * Merkt sich die letzten Suchbegriffe (neueste zuerst, dedupliziert, gekappt). Normale
 * SharedPreferences – als newline-getrennte Liste, weil die Reihenfolge zählt (StringSet wäre
 * ungeordnet).
 */
class SearchHistory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recent(): List<String> =
        prefs.getString(KEY_RECENT, "").orEmpty().split("\n").filter { it.isNotBlank() }

    fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val updated = (listOf(q) + recent().filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        prefs.edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    fun clear() = prefs.edit().remove(KEY_RECENT).apply()

    private companion object {
        const val PREFS_NAME = "hub_search_history"
        const val KEY_RECENT = "recent"
        const val MAX = 8
    }
}
