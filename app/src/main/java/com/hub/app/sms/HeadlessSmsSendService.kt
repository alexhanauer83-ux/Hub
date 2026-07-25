package com.hub.app.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/**
 * Pflichtkomponente für die Standard-SMS-App-Rolle: Über `ACTION_RESPOND_VIA_MESSAGE`
 * schickt das System z. B. die "Mit Nachricht antworten"-Funktion aus dem
 * Anruf-Bildschirm an die Standard-SMS-App. Fehlt dieser Service, wird die App nicht
 * als Standard-SMS-App akzeptiert.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyIntents.ACTION_RESPOND_VIA_MESSAGE) {
            handleRespondViaMessage(intent)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun handleRespondViaMessage(intent: Intent) {
        // Die Zieladresse steckt in der URI (z. B. "smsto:+49170...").
        val recipient = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (recipient == null || text.isNullOrBlank()) {
            Log.w(TAG, "Respond-via-message ohne Empfänger oder Text ignoriert")
            return
        }

        runCatching {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(text)
            if (parts.size == 1) {
                smsManager.sendTextMessage(recipient, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            }
        }.onFailure { Log.w(TAG, "Respond-via-message fehlgeschlagen", it) }
    }

    private object TelephonyIntents {
        // android.telephony.TelephonyManager.ACTION_RESPOND_VIA_MESSAGE
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }

    companion object {
        private const val TAG = "HeadlessSmsSend"
    }
}
