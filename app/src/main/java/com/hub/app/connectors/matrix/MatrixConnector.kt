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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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

    private val appContext: Context = context.applicationContext

    private val moshi = Moshi.Builder().build()
    private val uiaAdapter = moshi.adapter(UiaResponse::class.java)
    private val eventAdapter = moshi.adapter(MatrixEvent::class.java)

    private var cachedApi: Pair<String, MatrixApi>? = null

    // --- E2EE (experimentell) ---
    private val jsonMedia = "application/json".toMediaType()
    private var crypto: MatrixCrypto? = null
    // roomId -> ist der Raum verschlüsselt? (aus m.room.encryption-State bzw. Abfrage).
    private val encryptedRooms = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Baut die [MatrixCrypto]-Maschine beim ersten Bedarf und lädt dabei Geräte-/One-Time-Keys hoch.
     * Braucht userId **und** deviceId aus der Session; fehlt eines (z. B. Altsession vor E2EE),
     * bleibt Krypto aus und verschlüsselte Räume funktionieren erst nach erneutem Login.
     */
    private suspend fun ensureCrypto(api: MatrixApi, auth: String): MatrixCrypto? {
        crypto?.let { return it }
        val userId = credentials.userId ?: return null
        val deviceId = credentials.deviceId ?: return null
        val c = MatrixCrypto.create(appContext, userId, deviceId)
        crypto = c
        runCatching { c.drainOutgoing(api, auth) }
            .onFailure { Log.w(TAG, "E2EE: Schlüssel-Upload fehlgeschlagen", it) }
        return c
    }

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
        val requestBody = bytes.toRequestBody(mime.toMediaTypeOrNull())
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
                // E2EE-Maschine sicherstellen (idempotent; lädt beim ersten Mal die Schlüssel hoch).
                ensureCrypto(api, auth)
                // Roh-Sync: die OlmMachine braucht das ungefilterte JSON (To-Device, Ciphertext, Geräte-Listen).
                val root = JSONObject(api.syncRaw(auth, since, LONG_POLL_TIMEOUT_MS).string())
                val nextBatch = root.optString("next_batch", since ?: "")

                // Krypto-relevante Teile der Sync-Antwort in die Maschine einspeisen.
                crypto?.let { c ->
                    val toDevice = root.optJSONObject("to_device")?.optJSONArray("events")?.toString() ?: "[]"
                    val dl = root.optJSONObject("device_lists")
                    val fallback = root.optJSONArray("device_unused_fallback_key_types")?.toStringList()
                    runCatching {
                        c.onSyncChanges(
                            api, auth, toDevice,
                            dl?.optJSONArray("changed").toStringList(),
                            dl?.optJSONArray("left").toStringList(),
                            root.optJSONObject("device_one_time_keys_count").toIntMap(),
                            fallback, nextBatch
                        )
                    }.onFailure { Log.w(TAG, "E2EE: Sync-Verarbeitung fehlgeschlagen", it) }
                }

                if (!skipBacklog) {
                    val join = root.optJSONObject("rooms")?.optJSONObject("join")
                    val roomIds = join?.keys()
                    while (roomIds != null && roomIds.hasNext()) {
                        val roomId = roomIds.next()
                        val room = join.getJSONObject(roomId)
                        val stateEvents = room.optJSONObject("state")?.optJSONArray("events")
                        val timelineEvents = room.optJSONObject("timeline")?.optJSONArray("events")
                        // Raumname + Verschlüsselungsstatus aus State/Timeline merken.
                        noteRoomFacts(roomId, stateEvents)
                        noteRoomFacts(roomId, timelineEvents)
                        val roomName = roomNames[roomId] ?: ensureRoomName(api, auth, roomId)

                        if (timelineEvents != null) {
                            for (i in 0 until timelineEvents.length()) {
                                val evObj = timelineEvents.optJSONObject(i) ?: continue
                                ingestEvent(evObj, roomId, selfId, roomName)?.let { ingestSink.ingest(it) }
                            }
                        }
                    }
                }
                since = nextBatch
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
        val auth = bearer(token)
        val c = ensureCrypto(api, auth)
        // txnId muss pro Nachricht eindeutig sein (Idempotenz serverseitig).
        if (c != null && isRoomEncrypted(api, auth, roomId)) {
            // Verschlüsselter Raum: Klartext-Content bauen, Raum-Schlüssel verteilen, verschlüsseln,
            // als m.room.encrypted senden.
            val content = JSONObject().put("msgtype", "m.text").put("body", text).toString()
            val members = joinedMemberIds(api, auth, roomId)
            val encrypted = c.ensureSessionsAndEncrypt(api, auth, roomId, members, "m.room.message", content)
            api.sendEvent(auth, roomId, "m.room.encrypted", UUID.randomUUID().toString(), encrypted.toRequestBody(jsonMedia))
        } else {
            api.sendMessage(auth, roomId, UUID.randomUUID().toString(), SendRequest(body = text))
        }
        Unit
    }

    // --- intern --------------------------------------------------------------

    // roomId -> Anzeigename (Raumname); vermeidet, dass der rohe Raum-ID-Schlüssel als Titel steht.
    private val roomNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Holt den Raumnamen (gecached); null, wenn der Raum keinen gesetzten Namen hat. */
    private suspend fun ensureRoomName(api: MatrixApi, auth: String, roomId: String): String? {
        roomNames[roomId]?.let { return it }
        val name = runCatching {
            val r = api.roomName(auth, roomId)
            if (r.isSuccessful) r.body()?.name?.takeIf { it.isNotBlank() } else null
        }.getOrNull()
        if (name != null) roomNames[roomId] = name
        return name
    }

    private fun MatrixEvent.toIncomingMessage(roomId: String, selfId: String?, roomName: String?): IncomingMessage? {
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
            imageUri = imageUri,
            conversationTitle = roomName
        )
    }

    /**
     * Wandelt ein rohes Timeline-Event in eine [IncomingMessage]. Verschlüsselte Events
     * (`m.room.encrypted`) werden – wenn möglich – vorher entschlüsselt und das Klartext-Event
     * (Typ + Inhalt) mit den Metadaten des Wrapper-Events (Absender, Zeit, ID) kombiniert.
     * Scheitert die Entschlüsselung, bleibt das Original → Platzhalter wie bisher.
     */
    private fun ingestEvent(evObj: JSONObject, roomId: String, selfId: String?, roomName: String?): IncomingMessage? {
        val effective = if (evObj.optString("type") == "m.room.encrypted") {
            val clear = crypto?.decrypt(evObj.toString(), roomId)
            if (clear != null) {
                val clearObj = runCatching { JSONObject(clear) }.getOrNull()
                if (clearObj != null) {
                    JSONObject().apply {
                        put("type", clearObj.optString("type"))
                        put("content", clearObj.opt("content"))
                        put("sender", evObj.opt("sender"))
                        put("event_id", evObj.opt("event_id"))
                        put("origin_server_ts", evObj.opt("origin_server_ts"))
                    }
                } else evObj
            } else evObj
        } else evObj
        val event = runCatching { eventAdapter.fromJson(effective.toString()) }.getOrNull() ?: return null
        return event.toIncomingMessage(roomId, selfId, roomName)
    }

    /** Merkt sich Raumname (m.room.name) und Verschlüsselungsstatus (m.room.encryption) aus Events. */
    private fun noteRoomFacts(roomId: String, events: JSONArray?) {
        if (events == null) return
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            when (ev.optString("type")) {
                "m.room.name" -> ev.optJSONObject("content")?.optString("name")
                    ?.takeIf { it.isNotBlank() }?.let { roomNames[roomId] = it }
                "m.room.encryption" -> encryptedRooms[roomId] = true
            }
        }
    }

    /** Ist der Raum verschlüsselt? (aus Sync gemerkt, sonst per State-Abfrage; Ergebnis gecached.) */
    private suspend fun isRoomEncrypted(api: MatrixApi, auth: String, roomId: String): Boolean {
        encryptedRooms[roomId]?.let { return it }
        val enc = runCatching {
            val r = api.roomEncryption(auth, roomId)
            r.isSuccessful && !r.body()?.algorithm.isNullOrBlank()
        }.getOrDefault(false)
        encryptedRooms[roomId] = enc
        return enc
    }

    /** Beigetretene Mitglieder eines Raums (User-IDs) – Ziel der Schlüsselverteilung. */
    private suspend fun joinedMemberIds(api: MatrixApi, auth: String, roomId: String): List<String> =
        runCatching { api.joinedMembers(auth, roomId).joined.keys.toList() }.getOrDefault(emptyList())

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        val out = HashMap<String, Int>()
        val it = keys()
        while (it.hasNext()) { val k = it.next(); out[k] = optInt(k) }
        return out
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
