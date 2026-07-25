package com.hub.app.sms

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hub.app.data.local.entity.MessageCategory
import com.hub.app.data.source.IncomingMessage
import com.hub.app.data.source.MessageIngestSink
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.ReplyTarget
import com.hub.app.data.source.SourceCapability
import com.hub.app.data.source.SourceQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMS-Quelle über den `Telephony`-ContentProvider.
 *
 * Im Gegensatz zum Notification-Abgriff liefert diese Quelle den vollständigen Verlauf
 * und wird daher als [SourceQuality.API_NATIVE] geführt. Voraussetzung ist die
 * READ_SMS-Berechtigung; für das *Senden* zusätzlich SEND_SMS. Die Rolle als
 * Standard-SMS-App (siehe [SmsDefaultAppManager]) ist für das Lesen nicht zwingend, aber
 * für den zuverlässigen Empfang neuer Nachrichten über [SmsReceiver].
 */
class SmsMessageSource(private val context: Context) : MessageSource {

    override val sourceKey: String = SOURCE_KEY
    override val displayName: String = "SMS"
    override val quality: SourceQuality = SourceQuality.API_NATIVE
    override val capabilities: Set<SourceCapability> =
        setOf(SourceCapability.REPLY, SourceCapability.FULL_HISTORY)

    override suspend fun start(ingestSink: MessageIngestSink) {
        if (!hasReadPermission()) return
        importInbox(ingestSink)
    }

    override suspend fun stop() = Unit

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Liest den Posteingang aus dem ContentProvider. Bewusst begrenzt auf [IMPORT_LIMIT]
     * Nachrichten: Ein vollständiger Import kann bei langjährigen Verläufen zehntausende
     * Zeilen umfassen und würde den Feed fluten.
     */
    suspend fun importInbox(
        ingestSink: MessageIngestSink,
        limit: Int = IMPORT_LIMIT
    ) = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.THREAD_ID
        )

        runCatching {
            context.contentResolver.queryInbox(projection, limit)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)

                while (cursor.moveToNext()) {
                    ingestSink.ingest(
                        IncomingMessage(
                            sourceKey = SOURCE_KEY,
                            sourceLabel = displayName,
                            sourcePackageName = null,
                            externalId = cursor.getString(idIndex),
                            conversationId = cursor.getString(threadIndex),
                            sender = cursor.getString(addressIndex) ?: "Unbekannt",
                            content = cursor.getString(bodyIndex).orEmpty(),
                            timestamp = cursor.getLong(dateIndex),
                            category = MessageCategory.SMS,
                            hasQuickReply = hasSendPermission()
                        )
                    )
                }
            }
        }.onFailure { Log.w(TAG, "SMS-Import fehlgeschlagen", it) }
    }

    /**
     * Antwortet per SMS an die Adresse der Ursprungsnachricht.
     *
     * Achtung: `target.conversationId` ist die THREAD_ID, **keine** Telefonnummer – sie
     * taugt nicht als Zieladresse. Die echte Adresse wird deshalb über die Provider-Zeile
     * der Ursprungsnachricht aufgelöst.
     */
    override suspend fun sendReply(target: ReplyTarget, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val address = resolveAddress(target.messageId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Zieladresse konnte nicht ermittelt werden")
                )
            sendSms(address, text)
        }

    /**
     * Sendet eine SMS direkt an eine Telefonnummer (für das Verfassen neuer Nachrichten).
     * Setzt nur die SEND_SMS-Berechtigung voraus – Hub muss dafür **nicht** Standard-SMS-App
     * sein. Lange Texte werden automatisch in mehrteilige SMS zerlegt.
     */
    suspend fun sendSms(address: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSendPermission()) {
            return@withContext Result.failure(SecurityException("SEND_SMS nicht erteilt"))
        }
        if (address.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Keine Zielnummer"))
        }
        runCatching {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(text)
            if (parts.size == 1) {
                smsManager.sendTextMessage(address, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            }
        }
    }

    /**
     * Löst die Telefonnummer zu einer Hub-Message-ID auf.
     *
     * Es gibt zwei Herkünfte mit unterschiedlichem ID-Format, beide über
     * [IncomingMessage.stableId] mit "sms:" präfigiert:
     *  - [importInbox] verwendet die Provider-Row-ID (rein numerisch) → Nummer per Query.
     *  - [SmsReceiver] hat beim Empfang noch keine Row-ID und verwendet
     *    "<nummer>@<zeitstempel>" → die Nummer steht direkt in der ID.
     */
    private fun resolveAddress(messageId: String): String? {
        val rawId = messageId.removePrefix("$SOURCE_KEY:").takeIf { it != messageId } ?: return null

        if (!rawId.all { it.isDigit() }) {
            return rawId.substringBefore('@').takeIf { it.isNotBlank() && it != rawId }
        }

        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms._ID} = ?",
                arrayOf(rawId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun ContentResolver.queryInbox(projection: Array<String>, limit: Int): Cursor? =
        query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        )

    companion object {
        const val SOURCE_KEY = "sms"
        private const val TAG = "SmsMessageSource"
        private const val IMPORT_LIMIT = 500
    }
}
