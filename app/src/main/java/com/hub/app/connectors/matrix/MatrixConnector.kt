package com.hub.app.connectors.matrix

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
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Matrix-Connector über die Client-Server-HTTP-API (siehe [MatrixApi]).
 *
 * Deckt Login, Kontoregistrierung (UIA/dummy), Live-Sync und Senden ab. E2EE-Räume
 * werden nicht entschlüsselt (Platzhalter); unverschlüsselte Räume vollständig. Lokal
 * liegen alle Nachrichten SQLCipher-verschlüsselt, die Session in
 * [MatrixCredentialStore].
 */
class MatrixConnector(
    context: Context,
    private val credentials: MatrixCredentialStore = MatrixCredentialStore(context)
) : MessageSource {

    override val sourceKey: String = SOURCE_KEY
    override val displayName: String = "Matrix"
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> =
        setOf(SourceCapability.REPLY, SourceCapability.FULL_HISTORY)

    private val moshi = Moshi.Builder().build()
    private val uiaAdapter = moshi.adapter(UiaResponse::class.java)

    private var cachedApi: Pair<String, MatrixApi>? = null

    fun isConfigured(): Boolean = credentials.isConfigured()

    fun currentUserId(): String? = credentials.userId

    // --- Einrichtung ---------------------------------------------------------

    /** Meldet sich per Passwort an und speichert die Session. Liefert die User-ID. */
    suspend fun login(homeserver: String, username: String, password: String): Result<String> =
        runCatching {
            val base = normalizeHomeserver(homeserver)
            val api = buildApi(base)
            val response = api.login(LoginRequest(identifier = Identifier(user = username.trim()), password = password))
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(response.errorBody()?.string(), "Anmeldung fehlgeschlagen"))
            }
            val body = response.body() ?: throw IllegalStateException("Leere Antwort vom Server")
            storeSession(base, body.userId, body.accessToken, body.deviceId)
            body.userId
        }

    /**
     * Legt ein neues Konto an. Unterstützt den einfachen UIA-Fluss (`m.login.dummy`).
     * Verlangt der Server zusätzliche Schritte (Captcha, Nutzungsbedingungen, E-Mail,
     * Registration-Token), ist das über diese schlanke HTTP-Anbindung nicht leistbar –
     * dann kommt eine erklärende Fehlermeldung.
     */
    suspend fun register(homeserver: String, username: String, password: String): Result<String> =
        runCatching {
            val base = normalizeHomeserver(homeserver)
            val api = buildApi(base)
            val user = username.trim()

            // Schritt 1: ohne auth -> i. d. R. 401 mit Session + Flows.
            val first = api.register(RegisterRequest(username = user, password = password, auth = null))
            if (first.isSuccessful) {
                return@runCatching storeFromRegister(base, first.body())
            }
            if (first.code() != 401) {
                // Viele Server (u. a. matrix.org) deaktivieren die API-Registrierung.
                val serverMsg = errorMessage(first.errorBody()?.string(), "Registrierung fehlgeschlagen")
                throw IllegalStateException(
                    "$serverMsg\n\nDieser Homeserver erlaubt keine Registrierung über Hub. " +
                        "Lege dein Konto beim Anbieter (z. B. Element/Website) an und melde dich hier mit „Anmelden“ an."
                )
            }

            val uia = first.errorBody()?.string()?.let { runCatching { uiaAdapter.fromJson(it) }.getOrNull() }
            val session = uia?.session
                ?: throw IllegalStateException("Server lieferte keine Registrierungs-Session")

            val onlyDummy = uia.flows?.any { it.stages == listOf("m.login.dummy") } == true
            if (!onlyDummy) {
                val stages = uia.flows?.flatMap { it.stages }?.distinct()?.joinToString(", ").orEmpty()
                throw IllegalStateException(
                    "Dieser Homeserver verlangt zusätzliche Registrierungsschritte" +
                        (if (stages.isNotBlank()) " ($stages)" else "") +
                        ". Bitte lege das Konto direkt beim Anbieter an und melde dich hier an."
                )
            }

            // Schritt 2: mit dummy-auth.
            val second = api.register(
                RegisterRequest(username = user, password = password, auth = AuthDict("m.login.dummy", session))
            )
            if (!second.isSuccessful) {
                throw IllegalStateException(errorMessage(second.errorBody()?.string(), "Registrierung fehlgeschlagen"))
            }
            storeFromRegister(base, second.body())
        }

    fun signOut() {
        cachedApi = null
        credentials.clear()
    }

    // --- Kontakte / Räume ----------------------------------------------------

    data class MatrixContact(val roomId: String, val name: String)

    /** Startet einen Direkt-Chat mit einer Matrix-User-ID (z. B. @alice:matrix.org). */
    suspend fun startDirectChat(userId: String): Result<String> = runCatching {
        val (base, token) = requireSession()
        val api = buildApi(base)
        api.createRoom(bearer(token), CreateRoomRequest(invite = listOf(userId.trim()))).roomId
    }

    /** Verlässt einen Raum (Kontakt entfernen/aufräumen). */
    suspend fun leaveRoom(roomId: String): Result<Unit> = runCatching {
        val (base, token) = requireSession()
        val api = buildApi(base)
        val response = api.leaveRoom(bearer(token), roomId, emptyMap())
        if (!response.isSuccessful) throw IllegalStateException("Raum konnte nicht verlassen werden")
    }

    /** Sendet eine Nachricht in einen Raum (Compose/„Neue Nachricht"). */
    suspend fun sendToRoom(roomId: String, text: String): Result<Unit> =
        sendReply(ReplyTarget(messageId = "", conversationId = roomId), text)

    /** Lädt eine aufgenommene Audiodatei hoch und sendet sie als m.audio in den Raum. */
    suspend fun sendVoice(roomId: String, file: java.io.File): Result<Unit> = runCatching {
        val (base, token) = requireSession()
        val api = buildApi(base)
        val mime = "audio/mp4"
        val bytes = file.readBytes()
        val requestBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse(mime), bytes)
        val upload = api.uploadMedia(bearer(token), mime, file.name, requestBody)
        api.sendAudio(
            bearer(token), roomId, UUID.randomUUID().toString(),
            AudioSendRequest(
                body = "Sprachnachricht",
                url = upload.contentUri,
                info = AudioInfo(mimetype = mime, size = bytes.size.toLong())
            )
        )
        Unit
    }

    /** Beigetretene Räume als "Kontakte/Chats" – jeweils mit Anzeigenamen (sofern gesetzt). */
    suspend fun fetchContacts(): Result<List<MatrixContact>> = runCatching {
        val (base, token) = requireSession()
        val api = buildApi(base)
        val auth = bearer(token)
        api.joinedRooms(auth).joinedRooms.map { roomId ->
            val name = runCatching {
                val r = api.roomName(auth, roomId)
                if (r.isSuccessful) r.body()?.name else null
            }.getOrNull()
            MatrixContact(roomId, name ?: roomId)
        }
    }

    // --- Sync-Loop -----------------------------------------------------------

    override suspend fun start(ingestSink: MessageIngestSink) {
        if (!credentials.isConfigured()) {
            Log.i(TAG, "Matrix nicht eingerichtet – Connector startet nicht.")
            return
        }
        val base = credentials.homeserver ?: return
        val token = credentials.accessToken ?: return
        val api = buildApi(base)
        val auth = bearer(token)
        val selfId = credentials.userId

        var since = credentials.syncSince
        // Beim allerersten Sync (kein Token) nur den Startpunkt merken und den Backlog
        // NICHT einspeisen - der Hub soll neue Nachrichten sammeln, nicht die gesamte
        // Historie fluten. Ab dem zweiten Durchlauf werden Live-Ereignisse eingespeist.
        var skipBacklog = since == null
        var backoffMillis = INITIAL_BACKOFF_MILLIS

        while (currentCoroutineContext().isActive) {
            try {
                val response = api.sync(auth, since, LONG_POLL_TIMEOUT_MS)
                if (!skipBacklog) {
                    response.rooms?.join?.forEach { (roomId, room) ->
                        room.timeline?.events?.forEach { event ->
                            event.toIncomingMessage(roomId, selfId)?.let { ingestSink.ingest(it) }
                        }
                    }
                }
                since = response.nextBatch
                credentials.syncSince = since
                skipBacklog = false
                backoffMillis = INITIAL_BACKOFF_MILLIS
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    // Token ungültig/abgelaufen -> Session verwerfen, Loop beenden.
                    Log.w(TAG, "Matrix-Token abgelehnt (401) – Session wird zurückgesetzt.")
                    credentials.clear()
                    return
                }
                Log.w(TAG, "Sync-Fehler ${e.code()}, neuer Versuch in ${backoffMillis}ms", e)
                delay(backoffMillis)
                backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            } catch (e: Exception) {
                Log.w(TAG, "Sync fehlgeschlagen, neuer Versuch in ${backoffMillis}ms", e)
                delay(backoffMillis)
                backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    override suspend fun stop() = Unit // Die Sync-Coroutine wird von aussen abgebrochen.

    // --- Senden --------------------------------------------------------------

    override suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> = runCatching {
        val (base, token) = requireSession()
        val roomId = target.conversationId
            ?: throw IllegalArgumentException("Kein Raum für die Antwort bekannt")
        val api = buildApi(base)
        // txnId muss pro Nachricht eindeutig sein (Idempotenz serverseitig).
        api.sendMessage(bearer(token), roomId, UUID.randomUUID().toString(), SendRequest(body = text))
        Unit
    }

    // --- intern --------------------------------------------------------------

    private fun MatrixEvent.toIncomingMessage(roomId: String, selfId: String?): IncomingMessage? {
        // Eigene ausgehende Nachrichten nicht als Eingang spiegeln.
        if (sender != null && sender == selfId) return null

        var content: String
        var audioUri: String? = null
        var imageUri: String? = null
        when (type) {
            "m.room.message" -> {
                when (this.content?.msgtype) {
                    // Sprachnachricht/Audio: mxc:// in eine abspielbare Download-URL wandeln.
                    "m.audio" -> {
                        audioUri = resolveMxc(this.content.url)
                        content = this.content.body ?: "Sprachnachricht"
                    }
                    "m.image" -> {
                        imageUri = resolveMxc(this.content.url)
                        content = this.content.body ?: "Bild"
                    }
                    else -> content = this.content?.body ?: return null
                }
            }
            "m.room.encrypted" -> {
                // E2EE kann diese Anbindung nicht entschlüsseln (siehe MatrixApi-KDoc).
                content = "🔒 Verschlüsselte Nachricht (in Hub nicht entschlüsselbar)"
            }
            else -> return null
        }

        return IncomingMessage(
            sourceKey = SOURCE_KEY,
            sourceLabel = "Matrix",
            sourcePackageName = null,
            externalId = eventId ?: "$roomId:${timestamp ?: System.currentTimeMillis()}",
            conversationId = roomId,
            sender = sender ?: "Matrix",
            content = content,
            timestamp = timestamp ?: System.currentTimeMillis(),
            category = MessageCategory.MESSAGING,
            isContentRedacted = type == "m.room.encrypted",
            hasQuickReply = true,
            audioUri = audioUri,
            imageUri = imageUri
        )
    }

    /**
     * Wandelt eine mxc://-URI in eine abspielbare/ladbare HTTPS-Download-URL. Der
     * Access-Token wird als Query-Parameter angehängt (Legacy-Media-Endpunkt) – so können
     * MediaPlayer/Coil ohne zusätzliche Auth-Header darauf zugreifen. Auf Servern mit
     * ausschließlich authentifizierten Medien (Matrix 1.11+) kann das fehlschlagen.
     */
    private fun resolveMxc(mxc: String?): String? {
        if (mxc == null || !mxc.startsWith("mxc://")) return null
        val path = mxc.removePrefix("mxc://") // <server>/<mediaId>
        val base = credentials.homeserver ?: return null
        val token = credentials.accessToken ?: return null
        return "$base/_matrix/media/v3/download/$path?access_token=$token"
    }

    private fun storeFromRegister(base: String, body: RegisterResponse?): String {
        val userId = body?.userId ?: throw IllegalStateException("Leere Antwort vom Server")
        val token = body.accessToken
            ?: throw IllegalStateException("Server hat kein Access-Token geliefert (evtl. E-Mail-Bestätigung nötig)")
        storeSession(base, userId, token, body.deviceId)
        return userId
    }

    private fun storeSession(base: String, userId: String, token: String, deviceId: String?) {
        credentials.homeserver = base
        credentials.userId = userId
        credentials.accessToken = token
        credentials.deviceId = deviceId
        credentials.syncSince = null // frische Session -> beim ersten Sync Backlog überspringen
    }

    private fun requireSession(): Pair<String, String> {
        val base = credentials.homeserver
        val token = credentials.accessToken
        if (base.isNullOrBlank() || token.isNullOrBlank()) {
            throw IllegalStateException("Matrix ist nicht eingerichtet")
        }
        return base to token
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun buildApi(base: String): MatrixApi {
        cachedApi?.let { (cachedBase, api) -> if (cachedBase == base) return api }
        val client = OkHttpClient.Builder()
            // Muss über dem Long-Poll-Timeout liegen, sonst bricht OkHttp die offene
            // Sync-Verbindung selbst ab.
            .readTimeout((LONG_POLL_TIMEOUT_MS / 1000L) + 20L, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl("$base/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MatrixApi::class.java)
        cachedApi = base to api
        return api
    }

    private fun normalizeHomeserver(input: String): String {
        var s = input.trim().removeSuffix("/")
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    private fun errorMessage(errorBody: String?, fallback: String): String {
        val parsed = errorBody?.let { runCatching { uiaAdapter.fromJson(it) }.getOrNull() }
        return parsed?.error ?: fallback
    }

    companion object {
        const val SOURCE_KEY = "matrix"
        private const val TAG = "MatrixConnector"
        private const val LONG_POLL_TIMEOUT_MS = 30_000
        private const val INITIAL_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 120_000L
    }
}
