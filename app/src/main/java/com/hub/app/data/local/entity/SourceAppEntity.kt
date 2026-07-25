package com.hub.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine vom Nutzer (de-)aktivierbare Nachrichtenquelle. Für Notification-basierte Quellen
 * entspricht `sourceKey` "notif:<packageName>"; für API-Connectoren dem jeweiligen
 * Connector-Key (z. B. "telegram_bot"). Steuert, welche Apps im Onboarding/den
 * Einstellungen erfasst werden und ob eine Quelle als "priorisiert" gilt.
 */
@Entity(tableName = "source_apps")
data class SourceAppEntity(
    @PrimaryKey val sourceKey: String,
    val label: String,
    val packageName: String?,
    val enabled: Boolean = true,
    val isPriority: Boolean = false,
    /** true = über offene API angebunden (siehe Kernfunktion 2), false = Notification-Fallback */
    val isNativeConnector: Boolean = false
)
