package com.hub.app.snooze

import java.util.Calendar
import java.util.TimeZone

/**
 * Reine Zeitmathematik für die Snooze-Vorschläge ("smarte" Zeitpunkte) und die freie
 * Snooze-Wahl. Bewusst framework-frei (nur java.util.Calendar), damit sie in JVM-Unit-Tests
 * deterministisch prüfbar ist – der "Jetzt"-Zeitpunkt ist injizierbar. Die UI ruft die Methoden
 * ohne Argument auf und nutzt so den Default [System.currentTimeMillis].
 *
 * Verhalten ist unverändert aus `MessagePeekSheet` hierher umgezogen worden.
 */
object SnoozeTimes {

    /** Millisekunden von jetzt bis heute 18:00 (negativ, wenn schon vorbei). */
    fun millisUntilTonight(nowMillis: Long = System.currentTimeMillis()): Long =
        atTime(nowMillis, days = 0, hour = 18) - nowMillis

    /** Millisekunden von jetzt bis morgen 08:00. */
    fun millisUntilTomorrowMorning(nowMillis: Long = System.currentTimeMillis()): Long =
        atTime(nowMillis, days = 1, hour = 8) - nowMillis

    /** Millisekunden von jetzt bis zum nächsten Montag 08:00. */
    fun millisUntilNextWeek(nowMillis: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Mindestens einen Tag weiter, dann bis zum nächsten Montag.
        do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
        return c.timeInMillis - nowMillis
    }

    /** Zeitpunkt in [days] Tagen um [hour]:00 Uhr als Millis, ausgehend von [nowMillis]. */
    fun atTime(nowMillis: Long, days: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** UTC-Mitternacht des heutigen Tages – Vergleichsbasis für den (UTC-basierten) DatePicker. */
    fun todayStartUtcMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Fügt das (UTC-Mitternacht liefernde) DatePicker-Datum mit der lokalen Uhrzeit zusammen. */
    fun combineDateTime(
        dateUtcMillis: Long?,
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        dateUtcMillis?.let {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = it }
            cal.set(Calendar.YEAR, utc.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, utc.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        }
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
