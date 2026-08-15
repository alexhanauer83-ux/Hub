package com.hub.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reine JSON-Runde für [BackupData]: [toJson] → [parseBackup] muss alle Felder erhalten, und der
 * Import muss gegenüber kaputtem/teilweisem JSON sowie unbekannten Enum-Namen robust bleiben.
 */
class SettingsBackupTest {

    // Werte bewusst durchgängig abweichend von den Standardwerten, damit ein „aus Versehen Default"
    // nicht als bestanden durchrutscht.
    private val sample = BackupData(
        mutedSources = setOf("notif:com.whatsapp", "imap:1"),
        mutedConversations = setOf("notif:com.whatsapp\u0001Alice"),
        pinnedConversations = setOf("telegram:bot\u0001Bob"),
        pinnedSources = setOf("matrix:acc"),
        quietHoursEnabled = true,
        quietHoursStartMinutes = 23 * 60,
        quietHoursEndMinutes = 6 * 60,
        retentionDays = 30,
        rightSwipeAction = SwipeAction.DELETE.name,
        leftSwipeAction = SwipeAction.NONE.name,
        backgroundSyncEnabled = false,
        replaceOtherNotifications = true,
        gestureHintDismissed = true,
        themeMode = "DARK",
        dynamicColor = false
    )

    @Test
    fun rundlauf_erhaeltAlleFelder() {
        val restored = parseBackup(sample.toJson())

        assertEquals(sample.mutedSources, restored.mutedSources)
        assertEquals(sample.mutedConversations, restored.mutedConversations)
        assertEquals(sample.pinnedConversations, restored.pinnedConversations)
        assertEquals(sample.pinnedSources, restored.pinnedSources)
        assertEquals(sample.quietHoursEnabled, restored.quietHoursEnabled)
        assertEquals(sample.quietHoursStartMinutes, restored.quietHoursStartMinutes)
        assertEquals(sample.quietHoursEndMinutes, restored.quietHoursEndMinutes)
        assertEquals(sample.retentionDays, restored.retentionDays)
        assertEquals(sample.rightSwipeAction, restored.rightSwipeAction)
        assertEquals(sample.leftSwipeAction, restored.leftSwipeAction)
        assertEquals(sample.backgroundSyncEnabled, restored.backgroundSyncEnabled)
        assertEquals(sample.replaceOtherNotifications, restored.replaceOtherNotifications)
        assertEquals(sample.gestureHintDismissed, restored.gestureHintDismissed)
        assertEquals(sample.themeMode, restored.themeMode)
        assertEquals(sample.dynamicColor, restored.dynamicColor)
        // Vollständige Gleichheit als Sicherheitsnetz.
        assertEquals(sample, restored)
    }

    @Test
    fun kaputtesJson_ergibtStandardwerte() {
        assertEquals(BackupData(), parseBackup("das ist kein json"))
        assertEquals(BackupData(), parseBackup(""))
    }

    @Test
    fun teilweisesJson_fuelltFehlendeMitStandard() {
        val d = BackupData()
        val restored = parseBackup("""{ "version": 1, "retentionDays": 90 }""")

        assertEquals(90, restored.retentionDays)
        // Fehlende Felder bleiben auf den Standardwerten.
        assertEquals(d.rightSwipeAction, restored.rightSwipeAction)
        assertEquals(d.themeMode, restored.themeMode)
        assertEquals(d.mutedSources, restored.mutedSources)
        assertEquals(d.dynamicColor, restored.dynamicColor)
    }

    @Test
    fun unbekannterEnumName_faelltAufStandardZurueck() {
        val restored = parseBackup(
            """{ "version": 1, "rightSwipeAction": "MUELL", "themeMode": "REGENBOGEN" }"""
        )

        assertEquals(SwipeAction.READ.name, restored.rightSwipeAction)
        assertEquals("SYSTEM", restored.themeMode)
    }
}
