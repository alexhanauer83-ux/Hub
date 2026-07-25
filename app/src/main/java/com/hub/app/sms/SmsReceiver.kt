package com.hub.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.hub.app.data.local.entity.MessageCategory
import com.hub.app.data.source.IncomingMessage
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Empfängt eingehende SMS, sobald Hub Standard-SMS-App ist.
 *
 * `SMS_DELIVER` (nicht `SMS_RECEIVED`) ist die Action, die **ausschließlich** an die
 * Standard-SMS-App zugestellt wird. Wer sie empfängt, ist auch dafür verantwortlich, die
 * Nachricht in den Telephony-Provider zu schreiben – das System tut das nicht mehr
 * automatisch. Genau deshalb ist die Rolle in Hub opt-in: Wer sie aktiviert, verlässt
 * sich darauf, dass Hub den Empfang übernimmt.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Mehrteilige SMS kommen als mehrere PDUs desselben Absenders an und müssen
        // zu einer Nachricht zusammengesetzt werden.
        val first = messages.first()
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val address = first.displayOriginatingAddress ?: "Unbekannt"
        val timestamp = first.timestampMillis

        // goAsync haelt den Receiver am Leben, bis die Coroutine fertig ist - ohne das
        // darf nach onReceive nicht mehr auf den Context zugegriffen werden.
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Erst hier aufloesen: initialisiert Keystore + verschluesselte DB,
                // das gehoert nicht auf den Main-Thread von onReceive.
                ServiceLocator.messageRepository(appContext).ingest(
                    IncomingMessage(
                        sourceKey = SmsMessageSource.SOURCE_KEY,
                        sourceLabel = "SMS",
                        sourcePackageName = null,
                        // Kein Provider-Row-Id verfuegbar, solange die SMS noch nicht
                        // geschrieben wurde - Absender + Zeitstempel identifizieren sie
                        // eindeutig genug fuer den Upsert.
                        externalId = "$address@$timestamp",
                        conversationId = address,
                        sender = address,
                        content = body,
                        timestamp = timestamp,
                        category = MessageCategory.SMS,
                        hasQuickReply = true
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Eingehende SMS konnte nicht gespeichert werden", e)
            } finally {
                pendingResult.finish()
            }
        }

        // TODO(Phase 5+): Als Standard-SMS-App muss Hub die Nachricht zusaetzlich selbst
        // per Telephony.Sms.Inbox.insert(...) in den Provider schreiben, damit andere
        // Apps (Backup, Wear, Autofill fuer OTP-Codes) sie weiterhin sehen. Solange das
        // fehlt, ist der Hub-Feed vollstaendig, der System-Provider aber nicht - deshalb
        // ist die Rolle im Onboarding als "experimentell" gekennzeichnet.
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
