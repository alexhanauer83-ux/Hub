package com.hub.app.notification

import android.content.Context
import com.hub.app.ui.theme.ThemeMode
import com.hub.app.ui.theme.ThemeSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sicherung/Wiederherstellung der **nicht sensiblen** App-Einstellungen.
 *
 * Bewusst enthalten sind nur harmlose Vorlieben (Stummschaltungen, Anpinnungen, Ruhezeiten,
 * Wisch-Aktionen, Aufbewahrung, Darstellung). **Niemals** enthalten sind Zugangsdaten
 * (E-Mail-/Matrix-/Telegram-Konten) oder der Datenbank-Schlüssel – diese werden hier weder
 * gelesen noch geschrieben.
 *
 * [BackupData] ist ein reines, framework-freies Datenmodell; [toJson]/[parseBackup] serialisieren
 * mit `org.json` (versioniert, tolerant gegenüber fehlenden/unbekannten Feldern). Die Adapter
 * [exportFrom]/[applyTo] verbinden das Modell mit [NotificationSettings] und [ThemeSettings].
 */

/** Aktuelle Version des Sicherungsformats (für spätere, tolerante Migrationen). */
const val BACKUP_VERSION = 1

/**
 * Reines Abbild aller sicherbaren Einstellungen. Enum-Werte liegen als Enum-**Name** (String) vor,
 * damit die Serialisierung stabil und leicht lesbar bleibt. Die Standardwerte entsprechen exakt den
 * App-Standards und dienen [parseBackup] als Rückfallwerte.
 */
data class BackupData(
    val mutedSources: Set<String> = emptySet(),
    val mutedConversations: Set<String> = emptySet(),
    val pinnedConversations: Set<String> = emptySet(),
    val pinnedSources: Set<String> = emptySet(),
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinutes: Int = 22 * 60,
    val quietHoursEndMinutes: Int = 7 * 60,
    val retentionDays: Int = 7,
    val rightSwipeAction: String = SwipeAction.READ.name,
    val leftSwipeAction: String = SwipeAction.ARCHIVE.name,
    val backgroundSyncEnabled: Boolean = true,
    val replaceOtherNotifications: Boolean = false,
    val gestureHintDismissed: Boolean = false,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = true
)

/** Serialisiert die Sicherung als (hübsch eingerücktes) JSON. Set-Felder werden zu JSON-Arrays. */
fun BackupData.toJson(): String {
    val obj = JSONObject()
    obj.put("version", BACKUP_VERSION)
    obj.put("mutedSources", JSONArray(mutedSources.toList()))
    obj.put("mutedConversations", JSONArray(mutedConversations.toList()))
    obj.put("pinnedConversations", JSONArray(pinnedConversations.toList()))
    obj.put("pinnedSources", JSONArray(pinnedSources.toList()))
    obj.put("quietHoursEnabled", quietHoursEnabled)
    obj.put("quietHoursStartMinutes", quietHoursStartMinutes)
    obj.put("quietHoursEndMinutes", quietHoursEndMinutes)
    obj.put("retentionDays", retentionDays)
    obj.put("rightSwipeAction", rightSwipeAction)
    obj.put("leftSwipeAction", leftSwipeAction)
    obj.put("backgroundSyncEnabled", backgroundSyncEnabled)
    obj.put("replaceOtherNotifications", replaceOtherNotifications)
    obj.put("gestureHintDismissed", gestureHintDismissed)
    obj.put("themeMode", themeMode)
    obj.put("dynamicColor", dynamicColor)
    return obj.toString(2)
}

/**
 * Liest eine Sicherung tolerant ein: kaputtes JSON ergibt reine Standardwerte (kein Absturz),
 * fehlende Felder werden mit den Standards aufgefüllt und unbekannte Enum-Namen fallen auf den
 * jeweiligen Standard zurück (bereits hier normalisiert).
 */
fun parseBackup(json: String): BackupData {
    val d = BackupData()
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return d
    return BackupData(
        mutedSources = obj.optStringSet("mutedSources"),
        mutedConversations = obj.optStringSet("mutedConversations"),
        pinnedConversations = obj.optStringSet("pinnedConversations"),
        pinnedSources = obj.optStringSet("pinnedSources"),
        quietHoursEnabled = obj.optBoolean("quietHoursEnabled", d.quietHoursEnabled),
        quietHoursStartMinutes = obj.optInt("quietHoursStartMinutes", d.quietHoursStartMinutes),
        quietHoursEndMinutes = obj.optInt("quietHoursEndMinutes", d.quietHoursEndMinutes),
        retentionDays = obj.optInt("retentionDays", d.retentionDays),
        rightSwipeAction = swipeOrDefault(obj.optString("rightSwipeAction", d.rightSwipeAction), SwipeAction.READ).name,
        leftSwipeAction = swipeOrDefault(obj.optString("leftSwipeAction", d.leftSwipeAction), SwipeAction.ARCHIVE).name,
        backgroundSyncEnabled = obj.optBoolean("backgroundSyncEnabled", d.backgroundSyncEnabled),
        replaceOtherNotifications = obj.optBoolean("replaceOtherNotifications", d.replaceOtherNotifications),
        gestureHintDismissed = obj.optBoolean("gestureHintDismissed", d.gestureHintDismissed),
        themeMode = themeModeOrDefault(obj.optString("themeMode", d.themeMode), ThemeMode.SYSTEM).name,
        dynamicColor = obj.optBoolean("dynamicColor", d.dynamicColor)
    )
}

/** Liest den aktuellen (nicht sensiblen) Einstellungsstand aus – ohne jegliche Zugangsdaten. */
fun exportFrom(context: Context): BackupData {
    val ns = NotificationSettings(context)
    val ts = ThemeSettings(context)
    return BackupData(
        mutedSources = ns.mutedSources(),
        mutedConversations = ns.mutedConversations(),
        pinnedConversations = ns.pinnedConversations(),
        pinnedSources = ns.pinnedSources(),
        quietHoursEnabled = ns.quietHoursEnabled,
        quietHoursStartMinutes = ns.quietHoursStartMinutes,
        quietHoursEndMinutes = ns.quietHoursEndMinutes,
        retentionDays = ns.retentionDays,
        rightSwipeAction = ns.rightSwipeAction.name,
        leftSwipeAction = ns.leftSwipeAction.name,
        backgroundSyncEnabled = ns.backgroundSyncEnabled,
        replaceOtherNotifications = ns.replaceOtherNotifications,
        gestureHintDismissed = ns.gestureHintDismissed,
        themeMode = ts.mode.name,
        dynamicColor = ts.dynamicColor
    )
}

/**
 * Schreibt eine eingelesene Sicherung zurück. Nach einer Neuinstallation (leerer Ausgangszustand)
 * ist das eine originalgetreue Wiederherstellung; Set-Felder werden ergänzend gesetzt. Es werden
 * ausschließlich nicht sensible Prefs berührt – **keine** Zugangsdaten.
 */
fun applyTo(context: Context, data: BackupData) {
    val ns = NotificationSettings(context)
    data.mutedSources.forEach { ns.setMuted(it, true) }
    data.pinnedSources.forEach { ns.setPinned(it, true) }
    applyConversationKeys(data.mutedConversations) { sourceKey, groupValue ->
        ns.setConversationMuted(sourceKey, groupValue, true)
    }
    applyConversationKeys(data.pinnedConversations) { sourceKey, groupValue ->
        ns.setConversationPinned(sourceKey, groupValue, true)
    }
    ns.quietHoursEnabled = data.quietHoursEnabled
    ns.quietHoursStartMinutes = data.quietHoursStartMinutes
    ns.quietHoursEndMinutes = data.quietHoursEndMinutes
    ns.retentionDays = data.retentionDays
    ns.rightSwipeAction = swipeOrDefault(data.rightSwipeAction, SwipeAction.READ)
    ns.leftSwipeAction = swipeOrDefault(data.leftSwipeAction, SwipeAction.ARCHIVE)
    ns.backgroundSyncEnabled = data.backgroundSyncEnabled
    ns.replaceOtherNotifications = data.replaceOtherNotifications
    ns.gestureHintDismissed = data.gestureHintDismissed

    val ts = ThemeSettings(context)
    ts.mode = themeModeOrDefault(data.themeMode, ThemeMode.SYSTEM)
    ts.dynamicColor = data.dynamicColor
}

// --- Interne Helfer (rein) ---

// Konversations-Schlüssel sind "sourceKey\u0001groupValue" (siehe NotificationSettings.conversationKey).
// Beim Zurückschreiben zerlegen und über die öffentlichen Setter denselben Schlüssel neu aufbauen.
private const val CONVERSATION_SEPARATOR = '\u0001'

private fun applyConversationKeys(keys: Set<String>, apply: (String, String) -> Unit) {
    keys.forEach { key ->
        val idx = key.indexOf(CONVERSATION_SEPARATOR)
        if (idx >= 0) apply(key.substring(0, idx), key.substring(idx + 1)) else apply(key, "")
    }
}

private fun swipeOrDefault(name: String?, default: SwipeAction): SwipeAction =
    runCatching { SwipeAction.valueOf(name ?: default.name) }.getOrDefault(default)

private fun themeModeOrDefault(name: String?, default: ThemeMode): ThemeMode =
    runCatching { ThemeMode.valueOf(name ?: default.name) }.getOrDefault(default)

private fun JSONObject.optStringSet(key: String): Set<String> {
    val arr = optJSONArray(key) ?: return emptySet()
    val out = LinkedHashSet<String>(arr.length())
    for (i in 0 until arr.length()) {
        val value = arr.optString(i, "")
        if (value.isNotEmpty()) out.add(value)
    }
    return out
}
