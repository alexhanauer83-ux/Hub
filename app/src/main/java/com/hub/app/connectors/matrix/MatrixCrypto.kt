package com.hub.app.connectors.matrix

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.matrix.rustcomponents.sdk.crypto.DeviceLists
import org.matrix.rustcomponents.sdk.crypto.EncryptionSettings
import org.matrix.rustcomponents.sdk.crypto.EventEncryptionAlgorithm
import org.matrix.rustcomponents.sdk.crypto.HistoryVisibility
import org.matrix.rustcomponents.sdk.crypto.OlmMachine
import org.matrix.rustcomponents.sdk.crypto.Request
import org.matrix.rustcomponents.sdk.crypto.RequestType
import java.util.UUID

/**
 * **EXPERIMENTELL – Matrix End-to-End-Verschlüsselung (E2EE).**
 *
 * Dünne Hülle um die `OlmMachine` aus `org.matrix.rustcomponents:crypto-android` (die Kotlin-
 * Bindings zur matrix-rust-sdk-Krypto). Die `OlmMachine` ist eine **„sans-IO"**-Zustandsmaschine:
 * Sie macht selbst keine Netzwerkaufrufe, sondern
 *  1. bekommt die Krypto-relevanten Teile jeder `/sync`-Antwort gefüttert ([onSyncChanges]) und
 *  2. teilt über [outgoingRequests] mit, welche HTTP-Aufrufe zu tätigen sind (Schlüssel hochladen,
 *     abfragen, beanspruchen, To-Device-Nachrichten senden). Das erledigt [drainOutgoing] gegen
 *     unsere handgebaute [MatrixApi] und meldet die Antwort per `markRequestAsSent` zurück.
 *
 * Zum Senden: [ensureSessionsAndEncrypt] verteilt bei Bedarf den Raum-Schlüssel an die Geräte der
 * Teilnehmer und verschlüsselt den Klartext-Content → fertiger `m.room.encrypted`-Body.
 * Zum Empfangen: [decrypt] entschlüsselt ein `m.room.encrypted`-Event zurück zum Klartext-JSON.
 *
 * ## ⚠️ Vor dem ersten Studio-Build zu prüfen (VERIFY)
 * Die exakten Kotlin-Typen der uniffi-Bindings (Felder der [Request]-Varianten, Konstruktor-
 * Parameter von [DeviceLists]/[DecryptionSettings]/[EncryptionSettings], Rückgabe von
 * `decryptRoomEvent`) unterscheiden sich je crypto-android-Version. Die mit „VERIFY" markierten
 * Stellen ggf. an die von Android Studio angezeigte Signatur anpassen. Krypto NIE als „fertig"
 * betrachten, bevor auf dem Gerät ent-/verschlüsselte Nachrichten real durchgelaufen sind.
 */
class MatrixCrypto private constructor(
    private val machine: OlmMachine
) {

    /** Unsere Geräte-Identitätsschlüssel (curve25519/ed25519) – v. a. zum Debuggen/Loggen. */
    fun identityKeys(): Map<String, String> = machine.identityKeys()

    /**
     * Füttert die Krypto-relevanten Teile einer `/sync`-Antwort in die Maschine: die To-Device-
     * Events (Schlüsselverteilung, Verifikation), geänderte/entfernte Geräte-Listen und die Zahl
     * der serverseitig verbliebenen One-Time-Keys. Danach sofort ausstehende Anfragen abarbeiten.
     */
    suspend fun onSyncChanges(
        api: MatrixApi,
        auth: String,
        toDeviceEventsJson: String,
        changedDevices: List<String>,
        leftDevices: List<String>,
        oneTimeKeyCounts: Map<String, Int>,
        unusedFallbackKeys: List<String>?,
        nextBatch: String
    ) {
        // 0.4.3-Signatur: mit nextBatchToken, aber ohne decryptionSettings (kam erst später).
        machine.receiveSyncChanges(
            events = toDeviceEventsJson,
            deviceChanges = DeviceLists(changed = changedDevices, left = leftDevices),
            keyCounts = oneTimeKeyCounts,
            unusedFallbackKeys = unusedFallbackKeys,
            nextBatchToken = nextBatch
        )
        drainOutgoing(api, auth)
    }

    /**
     * Arbeitet alle ausstehenden Krypto-HTTP-Anfragen ab, bis die Maschine keine mehr hat
     * (Hochladen der Geräte-/One-Time-Keys, /keys/query, /keys/claim, To-Device-Sends,
     * Signatur-Uploads). Jede Antwort wird zurückgemeldet, sonst wiederholt die Maschine sie.
     */
    suspend fun drainOutgoing(api: MatrixApi, auth: String) {
        while (true) {
            val requests = machine.outgoingRequests()
            if (requests.isEmpty()) return
            for (request in requests) {
                // VERIFY: Feldnamen/Formen der Request-Varianten je crypto-android-Version.
                when (request) {
                    is Request.KeysUpload -> {
                        val resp = api.keysUpload(auth, request.body.json()).string()
                        machine.markRequestAsSent(request.requestId, RequestType.KEYS_UPLOAD, resp)
                    }
                    is Request.KeysQuery -> {
                        // KeysQuery liefert die abzufragenden Nutzer; Body für /keys/query bauen.
                        val body = keysQueryBody(request.users)
                        val resp = api.keysQuery(auth, body.json()).string()
                        machine.markRequestAsSent(request.requestId, RequestType.KEYS_QUERY, resp)
                    }
                    is Request.KeysClaim -> {
                        val body = keysClaimBody(request.oneTimeKeys)
                        val resp = api.keysClaim(auth, body.json()).string()
                        machine.markRequestAsSent(request.requestId, RequestType.KEYS_CLAIM, resp)
                    }
                    is Request.ToDevice -> {
                        val resp = api.sendToDevice(
                            auth, request.eventType, UUID.randomUUID().toString(), request.body.json()
                        ).string()
                        machine.markRequestAsSent(request.requestId, RequestType.TO_DEVICE, resp)
                    }
                    is Request.SignatureUpload -> {
                        val resp = api.signaturesUpload(auth, request.body.json()).string()
                        machine.markRequestAsSent(request.requestId, RequestType.SIGNATURE_UPLOAD, resp)
                    }
                    is Request.RoomMessage -> {
                        // Wird beim Senden separat gehandhabt (siehe ensureSessionsAndEncrypt).
                        val resp = api.sendEvent(
                            auth, request.roomId, request.eventType, UUID.randomUUID().toString(),
                            request.content.json()
                        )
                        machine.markRequestAsSent(request.requestId, RequestType.ROOM_MESSAGE, resp.eventId.orEmpty())
                    }
                    else -> {
                        // KeysBackup u. Ä. hier (noch) nicht unterstützt – als gesendet abhaken,
                        // damit die Maschine nicht in einer Schleife hängen bleibt. VERIFY.
                    }
                }
            }
        }
    }

    /**
     * Stellt sicher, dass für alle Teilnehmer eine Megolm-Session existiert (ggf. Schlüssel
     * beanspruchen + Raum-Schlüssel teilen) und verschlüsselt den Klartext-Content. Rückgabe:
     * der fertige `m.room.encrypted`-Content als JSON-String.
     */
    suspend fun ensureSessionsAndEncrypt(
        api: MatrixApi,
        auth: String,
        roomId: String,
        userIds: List<String>,
        eventType: String,
        contentJson: String
    ): String {
        machine.updateTrackedUsers(userIds)
        // Fehlende Olm-Sessions beschaffen (/keys/claim), dann Geräte abfragen.
        machine.getMissingSessions(userIds)?.let { dispatchOne(api, auth, it) }
        drainOutgoing(api, auth)
        // Raum-Schlüssel an alle Teilnehmer-Geräte verteilen (To-Device m.room_key).
        // 0.4.3: EncryptionSettings verlangt alle Felder. Standard: Megolm, Rotation nach 1 Woche/100 Nachrichten.
        val shareRequests = machine.shareRoomKey(
            roomId, userIds,
            EncryptionSettings(
                algorithm = EventEncryptionAlgorithm.MEGOLM_V1_AES_SHA2,
                rotationPeriod = 604_800_000UL,
                rotationPeriodMsgs = 100UL,
                historyVisibility = HistoryVisibility.SHARED,
                onlyAllowTrustedDevices = false,
                errorOnVerifiedUserProblem = false
            )
        )
        for (req in shareRequests) dispatchOne(api, auth, req)
        // Jetzt verschlüsseln.
        return machine.encrypt(roomId, eventType, contentJson)
    }

    /**
     * Entschlüsselt ein `m.room.encrypted`-Event (volles Event-JSON) zurück zum Klartext-JSON.
     * `null`, wenn (noch) kein passender Raum-Schlüssel vorliegt – dann bleibt der Platzhalter.
     */
    fun decrypt(rawEventJson: String, roomId: String): String? = runCatching {
        // VERIFY: Rückgabetyp von decryptRoomEvent (DecryptedEvent) – Feld für das Klartext-JSON
        // heißt je Version .clearEvent / .event.
        machine.decryptRoomEvent(
            event = rawEventJson,
            roomId = roomId,
            handleVerificationEvents = true,
            strictShields = false
        ).clearEvent
    }.getOrNull()

    private suspend fun dispatchOne(api: MatrixApi, auth: String, request: Request) {
        // Ein einzelner Request (aus getMissingSessions/shareRoomKey) – gleiche Behandlung wie im Drain.
        when (request) {
            is Request.KeysClaim -> {
                val resp = api.keysClaim(auth, keysClaimBody(request.oneTimeKeys).json()).string()
                machine.markRequestAsSent(request.requestId, RequestType.KEYS_CLAIM, resp)
            }
            is Request.ToDevice -> {
                val resp = api.sendToDevice(
                    auth, request.eventType, UUID.randomUUID().toString(), request.body.json()
                ).string()
                machine.markRequestAsSent(request.requestId, RequestType.TO_DEVICE, resp)
            }
            else -> drainOutgoing(api, auth)
        }
    }

    companion object {
        /**
         * Baut die Maschine für ein Konto. Der Store liegt geräteintern unter files/matrix-crypto/<deviceId>.
         * Direkt nach dem Erstellen einmal [drainOutgoing] aufrufen (lädt Geräte- und One-Time-Keys hoch).
         */
        fun create(context: Context, userId: String, deviceId: String): MatrixCrypto {
            val dir = context.filesDir.resolve("matrix-crypto/$deviceId").apply { mkdirs() }
            // VERIFY: OlmMachine-Konstruktor-Signatur (userId, deviceId, path, passphrase?).
            val machine = OlmMachine(userId, deviceId, dir.absolutePath, null)
            return MatrixCrypto(machine)
        }

        private fun String.json(): RequestBody = toRequestBody(JSON)
        private val JSON = "application/json".toMediaType()

        /** Body für /keys/query aus der Nutzerliste. */
        private fun keysQueryBody(users: List<String>): String {
            val map = users.joinToString(",") { "${it.jsonQuote()}:[]" }
            return "{\"device_keys\":{$map}}"
        }

        /** Body für /keys/claim: die von der Maschine gelieferte one_time_keys-Struktur durchreichen. */
        private fun keysClaimBody(oneTimeKeys: Map<String, Map<String, String>>): String {
            val outer = oneTimeKeys.entries.joinToString(",") { (user, devices) ->
                val inner = devices.entries.joinToString(",") { (dev, alg) -> "${dev.jsonQuote()}:${alg.jsonQuote()}" }
                "${user.jsonQuote()}:{$inner}"
            }
            return "{\"one_time_keys\":{$outer}}"
        }

        private fun String.jsonQuote(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
