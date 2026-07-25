package com.hub.app.connectors.imap

import android.util.Log
import com.hub.app.data.local.entity.MessageCategory
import com.hub.app.data.source.IncomingMessage
import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.SourceCapability
import com.hub.app.data.source.SourceQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress

/**
 * ## Status: unvollständig (bewusst)
 *
 * Dieser Connector ist **kein fertiges Feature**. Er zeigt, wie ein IMAP-Konto in die
 * [MessageSource]-Pipeline passt, und implementiert das Abholen neuer Mails, aber
 * mehrere produktionskritische Teile fehlen und sind unten als TODO markiert. Vor
 * einer Nutzung mit echten Konten muss mindestens Folgendes ergänzt werden:
 *
 *  - **IDLE statt Polling**: Aktuell wird im Intervall gepollt. Für Mobilbetrieb ist
 *    IMAP IDLE (Push) nötig, sonst kostet der Connector spürbar Akku. JavaMail
 *    unterstützt IDLE nur über `IMAPFolder.idle()` in einem eigenen Thread.
 *  - **Zustellung im Hintergrund**: Ohne Foreground-Service oder WorkManager stoppt
 *    Android die Verbindung, sobald die App in den Hintergrund geht.
 *  - **Credential-Handling**: Passwörter gehören wie das Telegram-Token in
 *    EncryptedSharedPreferences; OAuth2 (Gmail, Outlook) ist damit noch nicht abgedeckt.
 *  - **MIME-Parsing**: `extractPlainText` behandelt nur die einfachsten Fälle. Verschachtelte
 *    Multiparts, HTML-Only-Mails und Zeichensatz-Sonderfälle fehlen.
 *  - **Zustandsverwaltung**: UIDVALIDITY/UID-Tracking statt "letzte N Nachrichten", sonst
 *    werden nach einem Server-seitigen Reset Mails doppelt oder gar nicht geholt.
 */
class ImapConnector(
    private val config: ImapConfig
) : MessageSource {

    override val sourceKey: String = "imap:${config.accountId}"
    override val displayName: String = config.displayName
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.FULL_HISTORY)

    private var store: Store? = null

    override suspend fun start(ingestSink: MessageIngestSink) {
        while (currentCoroutineContext().isActive) {
            try {
                fetchRecent(ingestSink)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "IMAP-Abruf fehlgeschlagen für $sourceKey", e)
            }
            // TODO: durch IMAP IDLE ersetzen (siehe KDoc).
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching { store?.close() }
        store = null
    }

    private suspend fun fetchRecent(ingestSink: MessageIngestSink) = withContext(Dispatchers.IO) {
        val session = Session.getInstance(config.toProperties())
        val connectedStore = session.getStore(if (config.useSsl) "imaps" else "imap").apply {
            connect(config.host, config.port, config.username, config.password)
        }
        store = connectedStore

        connectedStore.getFolder("INBOX").use(Folder.READ_ONLY) { inbox ->
            val total = inbox.messageCount
            if (total == 0) return@use

            val start = (total - FETCH_WINDOW + 1).coerceAtLeast(1)
            for (message in inbox.getMessages(start, total)) {
                val from = (message.from?.firstOrNull() as? InternetAddress)
                val sender = from?.personal ?: from?.address ?: "Unbekannt"

                ingestSink.ingest(
                    IncomingMessage(
                        sourceKey = sourceKey,
                        sourceLabel = displayName,
                        sourcePackageName = null,
                        // TODO: echte IMAP-UID statt Message-Nummer - die Nummer aendert
                        // sich, sobald Mails geloescht werden, und erzeugt Duplikate.
                        externalId = "${message.messageNumber}",
                        conversationId = message.subject,
                        sender = sender,
                        content = message.subject.orEmpty(),
                        timestamp = message.receivedDate?.time ?: System.currentTimeMillis(),
                        category = MessageCategory.EMAIL
                    )
                )
            }
        }
    }

    /** Öffnet, verarbeitet und schließt einen Folder auch im Fehlerfall. */
    private inline fun <R> Folder.use(mode: Int, block: (Folder) -> R): R {
        open(mode)
        return try {
            block(this)
        } finally {
            runCatching { close(false) }
        }
    }

    private fun ImapConfig.toProperties() = Properties().apply {
        put("mail.store.protocol", if (useSsl) "imaps" else "imap")
        put("mail.imaps.host", host)
        put("mail.imaps.port", port.toString())
        put("mail.imaps.ssl.enable", useSsl.toString())
        // Verbindungs-Timeouts, sonst haengt der Abruf im Mobilfunk unbegrenzt.
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "15000")
    }

    companion object {
        private const val TAG = "ImapConnector"
        private const val POLL_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val FETCH_WINDOW = 50
    }
}

/**
 * Konfiguration eines IMAP-Kontos.
 *
 * TODO: [password] darf nicht im Klartext im Speicher gehalten werden - analog zu
 * [com.hub.app.connectors.telegram.TelegramCredentialStore] in EncryptedSharedPreferences
 * ablegen und nur fuer die Dauer der Verbindung entschluesseln.
 */
data class ImapConfig(
    val accountId: String,
    val displayName: String,
    val host: String,
    val port: Int = 993,
    val username: String,
    val password: String,
    val useSsl: Boolean = true
)
