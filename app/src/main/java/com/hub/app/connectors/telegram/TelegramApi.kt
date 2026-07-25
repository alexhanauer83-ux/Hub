package com.hub.app.connectors.telegram

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Telegram **Bot API** (nicht MTProto/TDLib).
 *
 * Bewusste Abgrenzung: Die Bot API ist reines HTTPS/JSON, braucht keine nativen
 * Bibliotheken und keinen Telefon-Login – ideal als Proof-of-Concept für die
 * Connector-Architektur. Ihre Grenze ist aber wichtig zu kennen: Ein Bot sieht **nur**
 * Nachrichten, die an ihn selbst gehen (Direktnachrichten an den Bot oder, bei
 * deaktiviertem Privacy-Mode, Gruppennachrichten in Gruppen, in denen er Mitglied ist).
 * Die *privaten* Chats des Nutzers sieht ein Bot prinzipbedingt nicht – dafür wäre ein
 * MTProto-Client (TDLib) mit echtem Nutzer-Login nötig, der native .so-Bibliotheken
 * mitbringt. Für private Chats bleibt bis dahin der Notification-Fallback.
 */
interface TelegramApi {

    /**
     * Long Polling. `timeout` hält die Verbindung serverseitig offen, bis etwas passiert –
     * deutlich sparsamer als kurzes Poll-Intervall im Sekundentakt.
     */
    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long?,
        @Query("timeout") timeoutSeconds: Int = 30,
        @Query("allowed_updates") allowedUpdates: String = "[\"message\"]"
    ): TelegramResponse<List<TelegramUpdate>>

    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Query("chat_id") chatId: Long,
        @Query("text") text: String
    ): TelegramResponse<TelegramMessage>

    /** Dient als Login-/Token-Validierung im Onboarding. */
    @GET("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): TelegramResponse<TelegramUser>

    companion object {
        const val BASE_URL = "https://api.telegram.org/"
    }
}

@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TelegramMessage? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    /** Unix-Zeit in Sekunden. */
    val date: Long,
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    val id: Long,
    @Json(name = "is_bot") val isBot: Boolean = false,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    val username: String? = null
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank {
            username ?: "Telegram-Nutzer $id"
        }
}

@JsonClass(generateAdapter = true)
data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    @Json(name = "first_name") val firstName: String? = null
)
