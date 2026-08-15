package com.hub.app.connectors.matrix

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Matrix **Client-Server-API** (HTTPS/JSON), analog zum Telegram-Connector bewusst ohne
 * schwergewichtiges SDK. Der Homeserver ist pro Konto konfigurierbar, daher wird das
 * Retrofit-Objekt im [MatrixConnector] mit der jeweiligen Basis-URL gebaut.
 *
 * ## Wichtige Grenze: E2E-Verschlüsselung
 * Diese Anbindung kann E2EE-Räume **nicht** entschlüsseln – das bräuchte die Krypto-
 * Bibliothek (Olm/Megolm) bzw. das Rust-SDK. Ereignisse vom Typ `m.room.encrypted`
 * werden daher als nicht-entschlüsselbarer Platzhalter eingespeist. Unverschlüsselte
 * Räume funktionieren vollständig. Lokal liegen alle Nachrichten trotzdem verschlüsselt
 * (SQLCipher-DB), Zugangsdaten in EncryptedSharedPreferences.
 */
interface MatrixApi {

    @POST("_matrix/client/v3/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    /**
     * Registrierung läuft über "User-Interactive Authentication" (UIA): Der erste Aufruf
     * ohne `auth` liefert i. d. R. 401 mit einer `session` und den nötigen Stages. Beim
     * zweiten Aufruf wird `auth` mit der Session (z. B. `m.login.dummy`) mitgeschickt.
     */
    @POST("_matrix/client/v3/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @GET("_matrix/client/v3/sync")
    suspend fun sync(
        @Header("Authorization") auth: String,
        @Query("since") since: String?,
        @Query("timeout") timeoutMs: Int = 30000
    ): SyncResponse

    @PUT("_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}")
    suspend fun sendMessage(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: String,
        @Path("txnId") txnId: String,
        @Body body: SendRequest
    ): SendResponse

    @GET("_matrix/client/v3/joined_rooms")
    suspend fun joinedRooms(@Header("Authorization") auth: String): JoinedRoomsResponse

    /** Lädt eine Datei (z. B. Sprachnachricht) in die Media-Ablage; liefert eine mxc://-URI. */
    @POST("_matrix/media/v3/upload")
    suspend fun uploadMedia(
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String,
        @Query("filename") filename: String,
        @Body body: okhttp3.RequestBody
    ): UploadResponse

    /** Sendet eine Audio-Nachricht (m.audio) mit der zuvor hochgeladenen mxc-URL. */
    @PUT("_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}")
    suspend fun sendAudio(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: String,
        @Path("txnId") txnId: String,
        @Body body: AudioSendRequest
    ): SendResponse

    /** Legt einen (Direkt-)Raum an und lädt optional Nutzer ein – Grundlage für "Neuer Chat". */
    @POST("_matrix/client/v3/createRoom")
    suspend fun createRoom(
        @Header("Authorization") auth: String,
        @Body body: CreateRoomRequest
    ): CreateRoomResponse

    @POST("_matrix/client/v3/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: String,
        @Body body: Map<String, String> = emptyMap()
    ): retrofit2.Response<okhttp3.ResponseBody>

    @GET("_matrix/client/v3/rooms/{roomId}/state/m.room.name/")
    suspend fun roomName(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: String
    ): Response<RoomNameResponse>

    // --- E2EE (experimentell): rohe JSON-Endpunkte, wie die OlmMachine sie erwartet ----------
    // Die OlmMachine liefert fertige Request-Bodies als JSON-String und verlangt die Antwort
    // ebenfalls als rohen JSON-String zurück (markRequestAsSent) → hier bewusst RequestBody/ResponseBody
    // statt typisierter Moshi-Modelle.

    /** Roh-Sync: liefert die komplette /sync-Antwort als JSON (für To-Device/Ciphertext/Device-Lists). */
    @GET("_matrix/client/v3/sync")
    suspend fun syncRaw(
        @Header("Authorization") auth: String,
        @Query("since") since: String?,
        @Query("timeout") timeoutMs: Int = 30000
    ): okhttp3.ResponseBody

    @POST("_matrix/client/v3/keys/upload")
    suspend fun keysUpload(
        @Header("Authorization") auth: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    @POST("_matrix/client/v3/keys/query")
    suspend fun keysQuery(
        @Header("Authorization") auth: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    @POST("_matrix/client/v3/keys/claim")
    suspend fun keysClaim(
        @Header("Authorization") auth: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    @PUT("_matrix/client/v3/sendToDevice/{eventType}/{txnId}")
    suspend fun sendToDevice(
        @Header("Authorization") auth: String,
        @Path("eventType") eventType: String,
        @Path("txnId") txnId: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    @POST("_matrix/client/v3/keys/signatures/upload")
    suspend fun signaturesUpload(
        @Header("Authorization") auth: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody

    /** Generisches Senden eines beliebigen Event-Typs (z. B. m.room.encrypted). */
    @PUT("_matrix/client/v3/rooms/{roomId}/send/{eventType}/{txnId}")
    suspend fun sendEvent(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: String,
        @Path("eventType") eventType: String,
        @Path("txnId") txnId: String,
        @Body body: okhttp3.RequestBody
    ): SendResponse

    companion object {
        /** Öffentlicher Standard-Homeserver als Vorbelegung im Setup. */
        const val DEFAULT_HOMESERVER = "https://matrix.org"
    }
}

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val identifier: Identifier,
    val password: String,
    val type: String = "m.login.password",
    @Json(name = "initial_device_display_name") val deviceName: String = "Hub"
)

@JsonClass(generateAdapter = true)
data class Identifier(
    val user: String,
    val type: String = "m.id.user"
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "user_id") val userId: String,
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "device_id") val deviceId: String?
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String,
    val auth: AuthDict? = null,
    @Json(name = "initial_device_display_name") val deviceName: String = "Hub"
)

@JsonClass(generateAdapter = true)
data class AuthDict(
    val type: String,
    val session: String? = null
)

@JsonClass(generateAdapter = true)
data class RegisterResponse(
    @Json(name = "user_id") val userId: String,
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "device_id") val deviceId: String?
)

/** Antwortkörper einer UIA-Anforderung (HTTP 401) bzw. eines Matrix-Fehlers. */
@JsonClass(generateAdapter = true)
data class UiaResponse(
    val session: String? = null,
    val flows: List<Flow>? = null,
    val completed: List<String>? = null,
    val errcode: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class Flow(val stages: List<String> = emptyList())

@JsonClass(generateAdapter = true)
data class SyncResponse(
    @Json(name = "next_batch") val nextBatch: String,
    val rooms: Rooms? = null
)

@JsonClass(generateAdapter = true)
data class Rooms(
    val join: Map<String, JoinedRoom>? = null
)

@JsonClass(generateAdapter = true)
data class JoinedRoom(
    val timeline: Timeline? = null,
    val state: RoomState? = null
)

@JsonClass(generateAdapter = true)
data class Timeline(val events: List<MatrixEvent>? = null)

@JsonClass(generateAdapter = true)
data class RoomState(val events: List<MatrixEvent>? = null)

@JsonClass(generateAdapter = true)
data class MatrixEvent(
    val type: String,
    val sender: String? = null,
    val content: EventContent? = null,
    @Json(name = "origin_server_ts") val timestamp: Long? = null,
    @Json(name = "event_id") val eventId: String? = null
)

@JsonClass(generateAdapter = true)
data class EventContent(
    val msgtype: String? = null,
    val body: String? = null,
    /** Bei m.room.name-State-Events der Raumname. */
    val name: String? = null,
    /** Bei Bild-/Datei-Nachrichten die mxc://-URL. */
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class SendRequest(
    val body: String,
    val msgtype: String = "m.text"
)

@JsonClass(generateAdapter = true)
data class SendResponse(@Json(name = "event_id") val eventId: String?)

@JsonClass(generateAdapter = true)
data class UploadResponse(@Json(name = "content_uri") val contentUri: String)

@JsonClass(generateAdapter = true)
data class AudioSendRequest(
    val body: String,
    val url: String,
    val info: AudioInfo? = null,
    val msgtype: String = "m.audio"
)

@JsonClass(generateAdapter = true)
data class AudioInfo(
    val mimetype: String? = null,
    val size: Long? = null,
    @Json(name = "duration") val durationMs: Int? = null
)

@JsonClass(generateAdapter = true)
data class JoinedRoomsResponse(@Json(name = "joined_rooms") val joinedRooms: List<String> = emptyList())

@JsonClass(generateAdapter = true)
data class CreateRoomRequest(
    val invite: List<String> = emptyList(),
    @Json(name = "is_direct") val isDirect: Boolean = true,
    val preset: String = "trusted_private_chat"
)

@JsonClass(generateAdapter = true)
data class CreateRoomResponse(@Json(name = "room_id") val roomId: String)

@JsonClass(generateAdapter = true)
data class RoomNameResponse(val name: String? = null)
