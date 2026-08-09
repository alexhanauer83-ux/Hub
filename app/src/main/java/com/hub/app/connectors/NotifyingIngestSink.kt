package com.hub.app.connectors

import android.content.Context
import com.hub.app.data.repository.MessageRepository
import com.hub.app.data.source.IncomingMessage
import com.hub.app.data.source.MessageIngestSink
import com.hub.app.notification.HubNotifier

/**
 * Dekoriert den Ingest-Weg der Pull-Connectoren (Matrix/Telegram/E-Mail): schreibt jede
 * Nachricht wie gehabt in den Feed **und** postet zusätzlich eine Hub-Benachrichtigung, wenn
 * es sich um eine wirklich neue, frische, eingehende Nachricht handelt.
 *
 * Bewusst nur hier (nicht im Repository): Fremd-App-Benachrichtigungen laufen über den
 * NotificationListener und **nicht** durch die [ConnectorRegistry] – so entstehen keine
 * Doppel-Alarme. Stummschaltung und Ruhezeiten prüft [HubNotifier] selbst.
 */
class NotifyingIngestSink(
    private val appContext: Context,
    private val repository: MessageRepository
) : MessageIngestSink {

    override suspend fun ingest(message: IncomingMessage) {
        val before = repository.getById(message.stableId)
        repository.ingest(message)
        val after = repository.getById(message.stableId)

        // Nur benachrichtigen, wenn die Nachricht tatsächlich gespeichert wurde (Quelle aktiv),
        // inhaltlich neu ist (keine Re-Delivery), noch ungelesen/aktiv und frisch – Letzteres
        // verhindert einen Benachrichtigungs-Sturm beim erstmaligen Nachladen alter Verläufe
        // (z. B. IMAP-Backfill von bis zu 50 Mails).
        val stored = after != null
        val contentChanged = before == null ||
            before.content != message.content || before.sender != message.sender
        val fresh = System.currentTimeMillis() - message.timestamp < FRESH_WINDOW_MS

        if (stored && contentChanged && fresh && !after!!.isRead && !after.isArchived) {
            HubNotifier.post(appContext, message)
        }
    }

    private companion object {
        /** „Frisch" = in den letzten 10 Minuten eingegangen. */
        const val FRESH_WINDOW_MS = 10 * 60 * 1000L
    }
}
