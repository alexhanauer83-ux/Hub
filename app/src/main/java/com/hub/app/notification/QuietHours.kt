package com.hub.app.notification

/**
 * Reine, framework-freie Ruhezeit-Logik – bewusst ohne Android-Abhängigkeiten, damit sie in
 * JVM-Unit-Tests deterministisch prüfbar ist. Alle Zeiten sind Minuten seit Mitternacht.
 *
 * Liegt [nowMin] im Fenster [[startMin], [endMin])? Start inklusiv, Ende exklusiv. Bei
 * [startMin] > [endMin] geht das Fenster über Mitternacht hinaus; [startMin] == [endMin]
 * bedeutet „kein Fenster" (nie Ruhezeit).
 */
fun quietHoursContains(startMin: Int, endMin: Int, nowMin: Int): Boolean =
    when {
        startMin == endMin -> false
        startMin < endMin -> nowMin in startMin until endMin
        else -> nowMin >= startMin || nowMin < endMin // über Mitternacht hinweg
    }
