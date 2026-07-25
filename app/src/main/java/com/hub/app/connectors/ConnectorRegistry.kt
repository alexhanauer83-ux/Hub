package com.hub.app.connectors

import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Hält die aktiven API-Connectoren und startet/stoppt sie einzeln.
 *
 * Das ist der Ort, an dem die Modularität aus Kernfunktion 2 konkret wird: Ein neuer
 * Connector muss nur [MessageSource] implementieren und hier registriert werden – weder
 * UI noch Repository müssen ihn kennen, weil alle Quellen über [MessageIngestSink] in
 * denselben Feed schreiben.
 */
class ConnectorRegistry(private val ingestSink: MessageIngestSink) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<String, Job>()
    private val connectors = ConcurrentHashMap<String, MessageSource>()

    fun register(connector: MessageSource) {
        connectors[connector.sourceKey] = connector
    }

    fun get(sourceKey: String): MessageSource? = connectors[sourceKey]

    fun all(): List<MessageSource> = connectors.values.toList()

    /** Startet einen Connector. Mehrfachaufrufe sind unschädlich (idempotent). */
    fun start(sourceKey: String) {
        val connector = connectors[sourceKey] ?: return
        if (running[sourceKey]?.isActive == true) return

        running[sourceKey] = scope.launch {
            connector.start(ingestSink)
        }
    }

    fun stop(sourceKey: String) {
        running.remove(sourceKey)?.cancel()
        scope.launch { connectors[sourceKey]?.stop() }
    }

    fun stopAll() {
        running.keys.toList().forEach { stop(it) }
    }
}
