package com.hub.app.connectors.telegram

import android.content.Context
import android.util.Log
import com.hub.app.data.local.entity.MessageCategory
import com.hub.app.data.source.IncomingMessage
import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.ReplyTarget
import com.hub.app.data.source.SourceCapability
import com.hub.app.data.source.SourceQuality
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Proof-of-Concept-Connector für Kernfunktion 2 über die Telegram **Bot API**.
 *
 * Zeigt exemplarisch, wie eine API-Quelle sich in dieselbe Pipeline hängt wie der
 * Notification-Listener: Sie implementiert [MessageSource], schreibt über
 * [MessageIngestSink] in denselben Room-Feed und wird als [SourceQuality.API_NATIVE]
 * geführt – also gegenüber dem Notification-Fallback bevorzugt.
 *
 * Zur Reichweite der Bot API siehe [TelegramApi]: Ein Bot sieht nur an ihn gerichtete
 * Nachrichten, nicht die privaten Chats des Nutzers.
 */
class TelegramBotConnector(
    context: Context,
    private val credentials: TelegramCredentialStore = TelegramCredentialStore(context)
) : MessageSource {

    override val sourceKey: String = SOURCE_KEY
    override val displayName: String = "Telegram"
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> =
        setOf(SourceCapability.REPLY, SourceCapability.FULL_HISTORY)

    private val api: TelegramApi by lazy {
        // Adapter kommen aus dem Moshi-Codegen (@JsonClass(generateAdapter = true)),
        // daher keine Reflection-Factory noetig.
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder()
            // Muss ueber dem Long-Poll-Timeout liegen, sonst bricht OkHttp die
            // absichtlich offen gehaltene Verbindung selbst ab.
            .readTimeout(LONG_POLL_TIMEOUT_SECONDS + 15L, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(TelegramApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TelegramApi::class.java)
    }

    fun isConfigured(): Boolean = !credentials.botToken.isNullOrBlank()

    /** Onboarding-Schritt: Token prüfen und bei Erfolg speichern. */
    suspend fun signIn(botToken: String): Result<String> = runCatching {
        val response = api.getMe(botToken)
        val user = response.result
        if (!response.ok || user == null) {
            throw IllegalArgumentException(
                response.description ?: "Token wurde von Telegram abgelehnt"
            )
        }
        credentials.botToken = botToken
        user.username ?: user.displayName
    }

    fun signOut() = credentials.clear()

    /**
     * Long-Polling-Schleife. Läuft, bis die Coroutine abgebrochen wird (siehe
     * [com.hub.app.connectors.ConnectorRegistry.stop]).
     */
    override suspend fun start(ingestSink: MessageIngestSink) {
        val token = credentials.botToken
        if (token.isNullOrBlank()) {
            Log.i(TAG, "Kein Bot-Token hinterlegt – Connector startet nicht.")
            return
        }

        var backoffMillis = INITIAL_BACKOFF_MILLIS

        while (currentCoroutineContext().isActive) {
            try {
                val offset = credentials.lastUpdateId.takeIf { it > 0 }?.let { it + 1 }
                val response = api.getUpdates(
                    token = token,
                    offset = offset,
                    timeoutSeconds = LONG_POLL_TIMEOUT_SECONDS
                )

                if (!response.ok) {
                    Log.w(TAG, "Telegram meldet Fehler: ${response.description}")
                    delay(backoffMillis)
                    backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                    continue
                }

                val updates = response.result.orEmpty()
                for (update in updates) {
                    update.message?.let { ingestSink.ingest(it.toIncomingMessage()) }
                    // Offset auch bei Updates ohne Nachricht fortschreiben, sonst liefert
                    // Telegram sie endlos erneut aus.
                    credentials.lastUpdateId = update.updateId
                }

                backoffMillis = INITIAL_BACKOFF_MILLIS
            } catch (e: CancellationException) {
                throw e // Abbruch nicht verschlucken
            } catch (e: Exception) {
                // Netzwerkfehler sind im Mobilbetrieb normal – exponentielles Backoff,
                // statt in einer Fehlerschleife Akku zu verbrennen.
                Log.w(TAG, "Polling fehlgeschlagen, neuer Versuch in ${backoffMillis}ms", e)
                delay(backoffMillis)
                backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    override suspend fun stop() = Unit // Die Polling-Coroutine wird von aussen abgebrochen.

    /**
     * Antwortet im ursprünglichen Chat. `conversationId` ist hier die Telegram-`chat_id`.
     */
    override suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> = runCatching {
        val token = credentials.botToken
            ?: throw IllegalStateException("Telegram ist nicht eingerichtet")
        val chatId = target.conversationId?.toLongOrNull()
            ?: throw IllegalArgumentException("Ungültige Chat-ID")

        val response = api.sendMessage(token, chatId, text)
        if (!response.ok) {
            throw IllegalStateException(response.description ?: "Senden fehlgeschlagen")
        }
    }

    private fun TelegramMessage.toIncomingMessage(): IncomingMessage {
        val senderName = from?.displayName
            ?: chat.title
            ?: chat.firstName
            ?: chat.username
            ?: "Telegram"

        return IncomingMessage(
            sourceKey = SOURCE_KEY,
            sourceLabel = displayName,
            sourcePackageName = null,
            externalId = "${chat.id}:$messageId",
            conversationId = chat.id.toString(),
            sender = senderName,
            content = text.orEmpty(),
            // Telegram liefert Unix-Sekunden, Room erwartet Millisekunden.
            timestamp = date * 1000L,
            category = MessageCategory.MESSAGING,
            hasQuickReply = true
        )
    }

    companion object {
        const val SOURCE_KEY = "telegram_bot"
        private const val TAG = "TelegramConnector"
        private const val LONG_POLL_TIMEOUT_SECONDS = 30
        private const val INITIAL_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 120_000L
    }
}
