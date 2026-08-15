package com.hub.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für die reine Ruhezeit-Logik [quietHoursContains]. */
class QuietHoursTest {

    // --- Normalfall: start < end (Fenster innerhalb eines Tages), z. B. 09:00–17:00 ---

    @Test
    fun normal_innerhalbDesFensters_istTrue() {
        // 12:00 liegt in 09:00–17:00
        assertTrue(quietHoursContains(9 * 60, 17 * 60, 12 * 60))
    }

    @Test
    fun normal_ausserhalbDesFensters_istFalse() {
        // 08:00 liegt vor 09:00
        assertFalse(quietHoursContains(9 * 60, 17 * 60, 8 * 60))
        // 18:00 liegt nach 17:00
        assertFalse(quietHoursContains(9 * 60, 17 * 60, 18 * 60))
    }

    @Test
    fun normal_startInklusiv_endeExklusiv() {
        // Start (09:00) ist inklusiv
        assertTrue(quietHoursContains(9 * 60, 17 * 60, 9 * 60))
        // Ende (17:00) ist exklusiv
        assertFalse(quietHoursContains(9 * 60, 17 * 60, 17 * 60))
    }

    // --- Über Mitternacht: start > end, z. B. 22:00–07:00 ---

    @Test
    fun ueberMitternacht_abendsUndMorgens_istTrue() {
        // 23:00 (spät abends, >= start)
        assertTrue(quietHoursContains(22 * 60, 7 * 60, 23 * 60))
        // 03:00 (nach Mitternacht, < end)
        assertTrue(quietHoursContains(22 * 60, 7 * 60, 3 * 60))
    }

    @Test
    fun ueberMitternacht_tagsueber_istFalse() {
        // 12:00 liegt außerhalb von 22:00–07:00
        assertFalse(quietHoursContains(22 * 60, 7 * 60, 12 * 60))
    }

    @Test
    fun ueberMitternacht_startInklusiv_endeExklusiv() {
        // Start (22:00) inklusiv
        assertTrue(quietHoursContains(22 * 60, 7 * 60, 22 * 60))
        // Ende (07:00) exklusiv
        assertFalse(quietHoursContains(22 * 60, 7 * 60, 7 * 60))
        // Direkt vor dem Ende (06:59) noch drin
        assertTrue(quietHoursContains(22 * 60, 7 * 60, 7 * 60 - 1))
    }

    // --- Entartetes Fenster: start == end → nie Ruhezeit ---

    @Test
    fun gleicherStartUndEnd_istImmerFalse() {
        assertFalse(quietHoursContains(8 * 60, 8 * 60, 8 * 60))
        assertFalse(quietHoursContains(8 * 60, 8 * 60, 12 * 60))
        assertFalse(quietHoursContains(0, 0, 0))
    }
}
