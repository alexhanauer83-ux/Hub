package com.hub.app.snooze

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests für die reine Snooze-Zeitmathematik [SnoozeTimes]. Die Standard-Zeitzone wird für die
 * Dauer der Tests fest auf Europe/Berlin gesetzt, damit die (lokalen) Ergebnisse unabhängig von
 * der Zeitzone des CI-Runners deterministisch sind.
 */
class SnoozeTimesTest {

    private val berlin: TimeZone = TimeZone.getTimeZone("Europe/Berlin")
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private var original: TimeZone = TimeZone.getDefault()

    @Before
    fun fixZeitzone() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(berlin)
    }

    @After
    fun restoreZeitzone() {
        TimeZone.setDefault(original)
    }

    /** Lokaler (Berlin-)Zeitpunkt als Millis. */
    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(berlin).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** UTC-Zeitpunkt als Millis. */
    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun heuteAbend_zielt_auf_18_Uhr_desselben_Tages() {
        // Sa. 15.08.2026, 12:00 Berlin
        val now = localMillis(2026, 8, 15, 12, 0)
        val delta = SnoozeTimes.millisUntilTonight(now)
        assertEquals(localMillis(2026, 8, 15, 18, 0) - now, delta)
        assertEquals(6L * 60 * 60 * 1000, delta) // 6 Stunden
    }

    @Test
    fun morgenFrueh_zielt_auf_08_Uhr_des_Folgetags() {
        val now = localMillis(2026, 8, 15, 12, 0)
        val delta = SnoozeTimes.millisUntilTomorrowMorning(now)
        assertEquals(localMillis(2026, 8, 16, 8, 0) - now, delta)
        assertEquals(20L * 60 * 60 * 1000, delta) // 20 Stunden
    }

    @Test
    fun naechsteWoche_zielt_auf_naechsten_Montag_08_Uhr() {
        val now = localMillis(2026, 8, 15, 12, 0)
        val delta = SnoozeTimes.millisUntilNextWeek(now)
        val target = Calendar.getInstance(berlin).apply { timeInMillis = now + delta }
        assertTrue("Ziel muss in der Zukunft liegen", delta > 0)
        assertEquals(Calendar.MONDAY, target.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(0, target.get(Calendar.SECOND))
    }

    @Test
    fun combineDateTime_verbindet_UTC_Datum_mit_lokaler_Uhrzeit() {
        // DatePicker liefert UTC-Mitternacht des gewählten Tages.
        val dateUtc = utcMillis(2026, 8, 20, 0, 0)
        val result = SnoozeTimes.combineDateTime(dateUtc, hour = 9, minute = 30)
        // Erwartet: 20.08.2026, 09:30 lokal (Berlin)
        assertEquals(localMillis(2026, 8, 20, 9, 30), result)
    }

    @Test
    fun todayStartUtcMillis_ist_UTC_Mitternacht_des_aktuellen_UTC_Tages() {
        // 15.08.2026, 23:00 UTC → UTC-Mitternacht ist 15.08.2026, 00:00 UTC
        val now = utcMillis(2026, 8, 15, 23, 0)
        assertEquals(utcMillis(2026, 8, 15, 0, 0), SnoozeTimes.todayStartUtcMillis(now))
    }
}
