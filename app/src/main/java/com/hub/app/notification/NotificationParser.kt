package com.hub.app.notification

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.hub.app.data.local.entity.MessageCategory
import com.hub.app.data.source.IncomingMessage

/**
 * Übersetzt eine [StatusBarNotification] in eine quellenneutrale [IncomingMessage].
 *
 * ## Bekannte Limitierung: redigierte Inhalte
 * Wenn der Nutzer systemweit oder pro App "Sensible Benachrichtigungsinhalte ausblenden"
 * aktiviert hat (bzw. die App `Notification.VISIBILITY_PRIVATE`/`VISIBILITY_SECRET` setzt),
 * liefert Android an Listener teilweise nur Platzhaltertext wie "1 neue Nachricht" statt des
 * echten Inhalts. Das ist eine bewusste Plattform-Einschränkung und **nicht** umgehbar –
 * kein API-Trick, kein Workaround. Wir erkennen den Fall heuristisch (siehe
 * [looksRedacted]) und markieren die Nachricht via `isContentRedacted`, damit die UI
 * ehrlich anzeigen kann, dass hier Inhalt fehlt, statt einen Platzhalter als echte
 * Nachricht auszugeben. Für betroffene Dienste ist die Lösung ein echter API-Connector
 * (Kernfunktion 2), nicht mehr Notification-Parsing.
 */
object NotificationParser {

    const val SOURCE_PREFIX = "notif:"

    /** Notifications, die keine Nachrichten sind (Medien-Player, laufende Downloads, Foreground-Services). */
    fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true // Sammel-Notification, Kinder kommen einzeln
        if (!sbn.isClearable) return true
        return when (n.category) {
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM -> true
            else -> false
        }
    }

    fun parse(sbn: StatusBarNotification, appLabel: String): IncomingMessage? {
        val extras: Bundle = sbn.notification.extras
        val title = extras.charSequenceValue(Notification.EXTRA_TITLE)
        val conversationTitle = extras.charSequenceValue(Notification.EXTRA_CONVERSATION_TITLE)

        // Bei MessagingStyle steckt der eigentliche Text in EXTRA_MESSAGES, nicht in EXTRA_TEXT.
        val messagingText = extractLatestMessagingStyleText(extras)
        val text = messagingText?.text
            ?: extras.charSequenceValue(Notification.EXTRA_BIG_TEXT)
            ?: extras.charSequenceValue(Notification.EXTRA_TEXT)

        // Ohne jeden Textinhalt ist der Eintrag im Hub wertlos.
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null

        val sender = messagingText?.sender ?: title ?: appLabel
        val content = text ?: ""

        return IncomingMessage(
            sourceKey = sourceKeyFor(sbn.packageName),
            sourceLabel = appLabel,
            sourcePackageName = sbn.packageName,
            // sbn.key ist über Updates derselben Notification stabil -> eine Zeile pro
            // Benachrichtigung. Der postTime wird bewusst NICHT angehängt: sonst erzeugt
            // jede Aktualisierung eine neue (ungelesene) Zeile, und bereits gelesene
            // Nachrichten tauchen wieder auf. Ob es sich um eine echte neue Nachricht handelt,
            // entscheidet beim Ingest der Inhaltsvergleich (siehe MessageRepository.ingest).
            externalId = sbn.key,
            conversationId = conversationTitle?.toString() ?: title?.toString(),
            sender = sender.toString(),
            content = content.toString(),
            timestamp = sbn.postTime,
            category = categoryOf(sbn.notification.category),
            isContentRedacted = looksRedacted(sbn, content.toString()),
            hasQuickReply = QuickReplyRegistry.findRemoteInputAction(sbn.notification) != null
        )
    }

    fun sourceKeyFor(packageName: String): String = "$SOURCE_PREFIX$packageName"

    private fun categoryOf(notificationCategory: String?): MessageCategory = when (notificationCategory) {
        Notification.CATEGORY_MESSAGE -> MessageCategory.MESSAGING
        Notification.CATEGORY_EMAIL -> MessageCategory.EMAIL
        Notification.CATEGORY_SOCIAL -> MessageCategory.SOCIAL
        Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL -> MessageCategory.CALL
        else -> MessageCategory.OTHER
    }

    private data class MessagingEntry(val sender: CharSequence?, val text: CharSequence?)

    /**
     * Liest den letzten Eintrag aus `EXTRA_MESSAGES` (MessagingStyle). Die Einträge sind
     * Bundles mit "text", "sender" bzw. ab API 28 "sender_person".
     */
    @Suppress("DEPRECATION")
    private fun extractLatestMessagingStyleText(extras: Bundle): MessagingEntry? {
        val messages = extras.parcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = messages.filterIsInstance<Bundle>().lastOrNull() ?: return null
        val text = last.getCharSequence("text") ?: return null
        // "sender_person" existiert seit API 28, minSdk ist 29 - kein Versions-Guard nötig.
        val sender = last.getCharSequence("sender")
            ?: (last.getParcelable("sender_person") as? android.app.Person)?.name
        return MessagingEntry(sender, text)
    }

    /**
     * Heuristik für redigierten Inhalt (siehe KDoc der Klasse). Wir prüfen die
     * Sichtbarkeitsstufe der Notification zusammen mit typischen Platzhaltertexten.
     * Bewusst konservativ: lieber ein redigierter Fall unerkannt als eine echte
     * Nachricht fälschlich als Platzhalter markiert.
     */
    private fun looksRedacted(sbn: StatusBarNotification, content: String): Boolean {
        val isPrivate = sbn.notification.visibility != Notification.VISIBILITY_PUBLIC
        if (!isPrivate) return false
        val normalized = content.trim().lowercase()
        val placeholderPatterns = listOf(
            "neue nachricht", "neue nachrichten",
            "new message", "new messages",
            "nachrichteninhalt ausgeblendet", "content hidden", "inhalt ausgeblendet"
        )
        return placeholderPatterns.any { normalized.contains(it) } &&
            normalized.length < 40
    }

    /** Rohe, noch nicht persistierte Anhänge einer Benachrichtigung. */
    data class RawAttachments(
        val picture: android.graphics.Bitmap?,
        val dataUri: android.net.Uri?,
        val dataMime: String?
    )

    /**
     * Findet Anhänge in einer Benachrichtigung:
     *  - `EXTRA_PICTURE` (BigPictureStyle) liefert eine Bitmap direkt.
     *  - MessagingStyle-Nachrichten können über "uri"/"type" ein Bild oder Audio anhängen;
     *    die URI ist eine Content-URI der Quell-App und wird später lokal kopiert.
     */
    fun extractAttachments(sbn: StatusBarNotification): RawAttachments {
        val extras = sbn.notification.extras
        val picture = extras.parcelable<android.graphics.Bitmap>(Notification.EXTRA_PICTURE)
        val (uri, mime) = latestMessagingData(extras)
        return RawAttachments(picture, uri, mime)
    }

    @Suppress("DEPRECATION")
    private fun latestMessagingData(extras: Bundle): Pair<android.net.Uri?, String?> {
        val messages = extras.parcelableArray(Notification.EXTRA_MESSAGES) ?: return null to null
        val bundleWithData = messages.filterIsInstance<Bundle>().lastOrNull { it.get("uri") != null }
            ?: return null to null
        val uri = bundleWithData.parcelable<android.net.Uri>("uri")
        val mime = bundleWithData.getString("type")
        return uri to mime
    }

    private fun Bundle.charSequenceValue(key: String): CharSequence? =
        getCharSequence(key)?.takeIf { it.isNotBlank() }

    // Bewusst die deprecatete, untypisierte Variante: Der typisierte getParcelable(key, Class)
    // braucht die konkrete Zielklasse; hier reicht der anschliessende as?-Cast, und die
    // Funktion bleibt fuer beliebige Parcelable-Typen (Bitmap, Uri) nutzbar.
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun <T> Bundle.parcelable(key: String): T? = getParcelable(key) as? T

    @Suppress("DEPRECATION")
    private fun Bundle.parcelableArray(key: String): Array<out android.os.Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArray(key, android.os.Parcelable::class.java)
        } else {
            getParcelableArray(key)
        }
}
