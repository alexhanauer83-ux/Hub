package com.hub.app.connectors.imap

import android.content.Context
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
 * IMAP-Connector: holt neue Mails eines Postfachs ab und speist sie in den Feed ein.
 * Zugangsdaten liegen verschlüsselt ([ImapCredentialStore]).
 *
 * Bekannte, bewusste Vereinfachungen für später:
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
    context: Context,
    private val credentials: ImapCredentialStore = ImapCredentialStore(context)
) : MessageSource {

    override val sourceKey: String = SOURCE_KEY
    override val displayName: String = "E-Mail"
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.FULL_HISTORY)

    private var store: Store? = null

    fun isConfigured(): Boolean = credentials.isConfigured()

    /** Prüft die Zugangsdaten per Testverbindung und speichert sie bei Erfolg. */
    suspend fun signIn(config: ImapConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            openStore(config).use2 { /* Verbindung + Login erfolgreich */ }
            credentials.save(config)
        }
    }

    fun signOut() {
        runCatching { store?.close() }
        store = null
        credentials.clear()
    }

    override suspend fun start(ingestSink: MessageIngestSink) {
        val config = credentials.load()
        if (config == null) {
            Log.i(TAG, "IMAP nicht eingerichtet – Connector startet nicht.")
            return
        }
        while (currentCoroutineContext().isActive) {
            try {
                fetchRecent(config, ingestSink)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "IMAP-Abruf fehlgeschlagen", e)
            }
            // TODO: durch IMAP IDLE ersetzen (siehe KDoc).
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        runCatching { store?.close() }
        store = null
    }

    private fun openStore(config: ImapConfig): Store {
        val session = Session.getInstance(config.toProperties())
        return session.getStore(if (config.useSsl) "imaps" else "imap").apply {
            connect(config.host, config.port, config.username, config.password)
        }
    }

    /** Store schließen, auch im Fehlerfall. */
    private inline fun <R> Store.use2(block: (Store) -> R): R = try {
        block(this)
    } finally {
        runCatching { close() }
    }

    private suspend fun fetchRecent(config: ImapConfig, ingestSink: MessageIngestSink) = withContext(Dispatchers.IO) {
        val connectedStore = openStore(config)
        store = connectedStore

        connectedStore.use2 { s ->
            s.getFolder("INBOX").use(Folder.READ_ONLY) { inbox ->
                val total = inbox.messageCount
                if (total == 0) return@use

                val start = (total - FETCH_WINDOW + 1).coerceAtLeast(1)
                for (message in inbox.getMessages(start, total)) {
                    val from = (message.from?.firstOrNull() as? InternetAddress)
                    val sender = from?.personal ?: from?.address ?: "Unbekannt"
                    val subject = message.subject.orEmpty()

                    ingestSink.ingest(
                        IncomingMessage(
                            sourceKey = sourceKey,
                            sourceLabel = config.displayName,
                            sourcePackageName = null,
                            // Message-ID ist stabil (anders als die Message-Nummer, die sich
                            // beim Löschen verschiebt); Fallback auf die Nummer.
                            externalId = messageId(message) ?: "${message.messageNumber}",
                            conversationId = subject.ifBlank { sender },
                            sender = sender,
                            content = buildPreview(subject, message),
                            timestamp = message.receivedDate?.time ?: System.currentTimeMillis(),
                            category = MessageCategory.EMAIL
                        )
                    )
                }
            }
        }
    }

    private fun messageId(message: javax.mail.Message): String? =
        runCatching { message.getHeader("Message-ID")?.firstOrNull() }.getOrNull()

    /** Betreff + kurzer Textauszug (einfaches Plaintext-Handling). */
    private fun buildPreview(subject: String, message: javax.mail.Message): String {
        val body = runCatching {
            when (val c = message.content) {
                is String -> c
                is javax.mail.Multipart -> (0 until c.count)
                    .map { c.getBodyPart(it) }
                    .firstOrNull { it.isMimeType("text/plain") }
                    ?.content as? String
                else -> null
            }
        }.getOrNull()?.trim()?.replace(Regex("\\s+"), " ")?.take(140)

        return if (body.isNullOrBlank()) subject else "$subject – $body"
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
        const val SOURCE_KEY = "imap"
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
    val displayName: String,
    val host: String,
    val port: Int = 993,
    val username: String,
    val password: String,
    val useSsl: Boolean = true
)
