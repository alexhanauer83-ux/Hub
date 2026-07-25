package com.hub.app.notification

import android.content.Context
import com.hub.app.connectors.matrix.MatrixConnector
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.data.source.MessageSource
import com.hub.app.data.source.ReplyTarget
import com.hub.app.di.ServiceLocator
import com.hub.app.sms.SmsMessageSource

/**
 * Leitet eine Antwort anhand des Quell-Schlüssels an die richtige [MessageSource] – dieselbe
 * Routing-Logik wie im HubViewModel, aber ViewModel-unabhängig, damit sie auch aus einem
 * BroadcastReceiver (Android-Auto-Sprachantwort, siehe [HubReplyReceiver]) nutzbar ist.
 */
object MessageReplyRouter {

    suspend fun reply(context: Context, messageId: String, text: String): Result<Unit> {
        val repository = ServiceLocator.messageRepository(context)
        val message = repository.getById(messageId)
            ?: return Result.failure(IllegalStateException("Nachricht nicht gefunden"))

        val source = sourceFor(context, message.sourceKey)
        val result = source.sendReply(
            ReplyTarget(messageId = message.id, conversationId = message.conversationId),
            text
        )
        if (result.isSuccess) {
            repository.markRead(messageId)
            repository.recordOutgoing(message, text)
        }
        return result
    }

    suspend fun markRead(context: Context, messageId: String) {
        ServiceLocator.messageRepository(context).markRead(messageId)
    }

    private fun sourceFor(context: Context, sourceKey: String): MessageSource = when (sourceKey) {
        MatrixConnector.SOURCE_KEY -> ServiceLocator.matrixConnector(context)
        TelegramBotConnector.SOURCE_KEY -> ServiceLocator.telegramConnector(context)
        SmsMessageSource.SOURCE_KEY -> SmsMessageSource(context)
        else -> NotificationMessageSource(context)
    }
}
