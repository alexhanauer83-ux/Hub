package com.hub.app.connectors.matrix

import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.ReplyTarget
import com.hub.app.data.source.SourceCapability
import com.hub.app.data.source.SourceQuality

/**
 * ## Status: Platzhalter, nicht implementiert
 *
 * Dieser Connector definiert nur den Vertrag – es findet **keine** Kommunikation mit
 * einem Matrix-Homeserver statt. Er existiert, damit die Erweiterungsstelle in der
 * Architektur sichtbar ist und ein späterer Ausbau nichts anderes anfassen muss.
 *
 * ### Warum hier nichts vorgetäuscht wird
 * Matrix sinnvoll anzubinden ist deutlich mehr Arbeit als bei Telegram oder IMAP:
 *
 *  - Das in der Aufgabenstellung genannte `matrix-android-sdk2` ist faktisch abgelöst;
 *    aktuell ist das **Matrix Rust SDK** (via Kotlin-Bindings) der gepflegte Weg. Beide
 *    bringen erhebliche Abhängigkeiten mit (Rust-Bindings bzw. Realm/OLM als native Libs).
 *  - **E2E-Verschlüsselung** ist bei Matrix der Normalfall, nicht die Ausnahme. Ein
 *    Client muss Geräte-Verifikation, Key-Backup und Cross-Signing beherrschen – ohne
 *    das sieht der Nutzer in verschlüsselten Räumen ausschließlich "Kann nicht
 *    entschlüsselt werden".
 *  - Der **Sync-Loop** (`/sync` mit `since`-Token bzw. Sliding Sync) braucht persistenten
 *    Zustand und läuft im Mobilbetrieb sinnvoll nur über Push (Sygnal/UnifiedPush),
 *    nicht über Dauer-Polling.
 *
 * Eine halbe Implementierung wäre hier schlechter als keine: Sie würde in genau den
 * verschlüsselten Räumen scheitern, in denen die meisten Nutzer tatsächlich schreiben.
 * Bis dahin greift für Matrix-Clients wie Element der Notification-Fallback aus
 * Kernfunktion 1.
 *
 * ### Nächste Schritte für eine echte Anbindung
 *  1. Matrix Rust SDK (Kotlin-Bindings) als Abhängigkeit aufnehmen.
 *  2. Login-Flow (Homeserver-Discovery, Passwort oder SSO) im Onboarding ergänzen.
 *  3. Session-/Krypto-Store verschlüsselt persistieren (analog SQLCipher-Ansatz der App).
 *  4. Sync-Ergebnisse auf [com.hub.app.data.source.IncomingMessage] abbilden und über
 *     [MessageIngestSink] einspeisen – ab hier ist der Rest der App bereits fertig.
 */
class MatrixConnector : MessageSource {

    override val sourceKey: String = SOURCE_KEY
    override val displayName: String = "Matrix"
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> =
        setOf(SourceCapability.REPLY, SourceCapability.FULL_HISTORY)

    /** Immer false: Ohne SDK-Anbindung lässt sich dieser Connector nicht einrichten. */
    fun isConfigured(): Boolean = false

    override suspend fun start(ingestSink: MessageIngestSink) {
        throw NotImplementedError(
            "Matrix-Connector ist nicht implementiert (siehe KDoc). Für Matrix-Clients " +
                "greift derzeit der Notification-Fallback."
        )
    }

    override suspend fun stop() = Unit

    override suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> =
        Result.failure(NotImplementedError("Matrix-Connector ist nicht implementiert"))

    companion object {
        const val SOURCE_KEY = "matrix"
    }
}
