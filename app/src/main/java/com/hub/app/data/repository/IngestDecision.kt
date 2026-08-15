package com.hub.app.data.repository

/**
 * Reines Prädikat für die Ingest-Deduplizierung: Handelt es sich beim erneuten Einlesen um
 * eine Re-Delivery (unveränderter Inhalt UND Absender) oder um eine echte neue Nachricht?
 *
 * Bewusst framework-frei und ohne Room-Abhängigkeit, damit es in JVM-Unit-Tests prüfbar ist.
 * Gibt es keine bestehende Nachricht, werden für [existingContent]/[existingSender] `null`
 * übergeben → immer `false` (kann keine Re-Delivery sein).
 */
fun isSameContent(
    existingContent: String?,
    existingSender: String?,
    newContent: String?,
    newSender: String?
): Boolean = existingContent == newContent && existingSender == newSender
