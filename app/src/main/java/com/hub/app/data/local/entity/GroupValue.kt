package com.hub.app.data.local.entity

/**
 * Reine Gruppierungsregel einer Nachricht – spiegelt exakt die SQL-Logik
 * `COALESCE(NULLIF(conversationId, ''), sender)` (siehe MessageDao): ist [conversationId]
 * leer oder null, wird nach [sender] gruppiert, sonst nach [conversationId]. Framework-frei,
 * damit sie in JVM-Unit-Tests prüfbar ist.
 */
fun groupValue(conversationId: String?, sender: String): String =
    conversationId?.takeIf { it.isNotBlank() } ?: sender
