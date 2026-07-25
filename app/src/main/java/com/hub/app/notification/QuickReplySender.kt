package com.hub.app.notification

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Sendet eine Antwort über die RemoteInput-Action der Original-Notification – also über
 * genau den Weg, den auch Android Auto oder Wear OS nutzen. Die Quell-App (WhatsApp,
 * Telegram, Signal …) wird dabei nicht geöffnet und muss nichts über Hub wissen.
 *
 * Ablauf:
 *  1. Ein leerer Intent dient als Träger für die Antwortdaten.
 *  2. [RemoteInput.addResultsToIntent] schreibt den Text unter dem `resultKey`, den die
 *     Quell-App in ihrem RemoteInput deklariert hat, in diesen Intent.
 *  3. `setResultsSource(SOURCE_FREE_FORM_INPUT)` markiert die Eingabe als frei getippt.
 *  4. Der so befüllte Intent wird über die [PendingIntent] der App gesendet – die App
 *     empfängt ihn, als käme er aus ihrer eigenen Antwort-Action.
 *
 * Fehlerfälle, die hier bewusst als [Result.failure] zurückkommen statt zu crashen:
 *  - [PendingIntent.CanceledException]: Die Notification wurde inzwischen entfernt oder
 *    die Quell-App neu gestartet. Die PendingIntent ist dann tot – die Antwort kann
 *    nicht zugestellt werden, und der Nutzer muss die App öffnen.
 *  - Kein Registry-Eintrag: siehe [QuickReplyRegistry] – nach einem Geräteneustart oder
 *    dem Verwerfen der Notification existiert keine gültige Reply-Action mehr, auch wenn
 *    die Nachricht selbst noch im Hub steht.
 */
object QuickReplySender {

    private const val TAG = "QuickReplySender"

    fun send(context: Context, messageId: String, text: String): Result<Unit> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Leere Antwort"))
        }

        val action = QuickReplyRegistry.get(messageId)
            ?: return Result.failure(
                IllegalStateException(
                    "Keine gültige Antwort-Action mehr vorhanden – die Benachrichtigung " +
                        "wurde entfernt oder das Gerät seither neu gestartet."
                )
            )

        val intent = Intent()
        val results = Bundle().apply {
            putCharSequence(action.replyRemoteInput.resultKey, text)
        }

        // addResultsToIntent erwartet alle RemoteInputs der Action, nicht nur den einen,
        // in den wir schreiben - sonst verwerfen manche Apps das Ergebnis.
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)

        return runCatching {
            action.pendingIntent.send(context, 0, intent)
        }.map { }.onFailure { error ->
            Log.w(TAG, "Quick Reply fehlgeschlagen für $messageId", error)
        }
    }
}
