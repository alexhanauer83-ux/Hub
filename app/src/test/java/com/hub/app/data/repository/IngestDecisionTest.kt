package com.hub.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für das reine Ingest-Dedup-Prädikat [isSameContent]. */
class IngestDecisionTest {

    @Test
    fun gleicherInhaltUndAbsender_istTrue() {
        assertTrue(isSameContent("Hallo", "Alice", "Hallo", "Alice"))
    }

    @Test
    fun geaenderterInhalt_istFalse() {
        assertFalse(isSameContent("Hallo", "Alice", "Servus", "Alice"))
    }

    @Test
    fun geaenderterAbsender_istFalse() {
        assertFalse(isSameContent("Hallo", "Alice", "Hallo", "Bob"))
    }

    @Test
    fun keinBestehenderEintrag_istFalse() {
        // existing == null → Content/Sender werden als null übergeben; eine echte Nachricht
        // hat nie null-Inhalt, also nie eine Re-Delivery.
        assertFalse(isSameContent(null, null, "Hallo", "Alice"))
    }
}
