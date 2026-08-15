package com.hub.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests für die reine Gruppierungsregel [groupValue] (spiegelt COALESCE(NULLIF(conversationId,''), sender)). */
class GroupValueTest {

    @Test
    fun conversationIdVorhanden_wirdVerwendet() {
        assertEquals("room-42", groupValue("room-42", "Alice"))
    }

    @Test
    fun conversationIdNull_faelltAufSenderZurueck() {
        assertEquals("Alice", groupValue(null, "Alice"))
    }

    @Test
    fun conversationIdLeer_faelltAufSenderZurueck() {
        assertEquals("Alice", groupValue("", "Alice"))
    }

    @Test
    fun conversationIdNurLeerzeichen_faelltAufSenderZurueck() {
        // isNotBlank(): reine Whitespace-Werte gelten wie leer
        assertEquals("Alice", groupValue("   ", "Alice"))
    }
}
