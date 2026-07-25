package com.hub.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Empfängt Antwort-/Gelesen-Aktionen aus Hubs eigenen Benachrichtigungen – insbesondere die
 * **Sprachantwort aus Android Auto**, die als [RemoteInput] zurückkommt. Die eigentliche
 * Zustellung übernimmt der [MessageReplyRouter] (WhatsApp-RemoteInput / Matrix / Telegram / SMS).
 */
class HubReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)?.toString().orEmpty()
                        if (text.isNotBlank()) {
                            MessageReplyRouter.reply(appContext, messageId, text)
                                .onFailure { Log.w(TAG, "Auto-Antwort fehlgeschlagen", it) }
                        }
                    }
                    ACTION_MARK_READ -> MessageReplyRouter.markRead(appContext, messageId)
                }
                // Nachricht ist beantwortet/gelesen -> Hub-Benachrichtigung schließen.
                NotificationManagerCompat.from(appContext).cancel(messageId.hashCode())
            } catch (e: Exception) {
                Log.w(TAG, "Reply-Broadcast fehlgeschlagen", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "HubReplyReceiver"
        const val ACTION_REPLY = "com.hub.app.action.REPLY"
        const val ACTION_MARK_READ = "com.hub.app.action.MARK_READ"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val KEY_REPLY_TEXT = "key_reply_text"
    }
}
