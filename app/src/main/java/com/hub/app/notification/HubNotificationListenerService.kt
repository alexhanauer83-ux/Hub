package com.hub.app.notification

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.hub.app.data.local.entity.SourceAppEntity
import com.hub.app.data.repository.MessageRepository
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kernfunktion 1: greift Benachrichtigungen aller (bzw. der vom Nutzer freigegebenen)
 * Apps ab und speist sie in denselben Feed ein wie die API-Connectoren.
 *
 * Manifest-Anforderungen (siehe AndroidManifest.xml):
 *  - `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` – ohne
 *    diese Signatur-Permission darf ausschließlich das System den Service binden.
 *  - Intent-Filter auf `android.service.notification.NotificationListenerService`.
 *  - `android:exported="true"` (Android 12+ Pflicht bei Intent-Filtern), da das System
 *    von außerhalb der App bindet.
 *
 * Die Berechtigung selbst kann eine App **nicht** per Runtime-Dialog anfordern; der Nutzer
 * muss sie in den Systemeinstellungen erteilen. Siehe [NotificationAccess].
 */
class HubNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appLabelCache = mutableMapOf<String, String>()

    // Lazy statt in onCreate: Der erste Zugriff initialisiert Keystore und die
    // SQLCipher-Datenbank. onCreate laeuft auf dem Main-Thread - so passiert das
    // stattdessen in der IO-Coroutine von handleNotification.
    private val repository: MessageRepository by lazy { ServiceLocator.messageRepository(this) }
    private val attachmentStore: AttachmentStore by lazy { AttachmentStore(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        // Bereits sichtbare Notifications einmalig einlesen, damit der Hub direkt nach
        // Erteilung der Berechtigung nicht leer wirkt.
        runCatching { activeNotifications }
            .getOrNull()
            ?.forEach { handleNotification(it) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        QuickReplyRegistry.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Die Nachricht bleibt im Hub erhalten (das ist der Sinn eines Hubs), aber die
        // Quick-Reply-PendingIntent ist ab jetzt ungültig.
        QuickReplyRegistry.removeByNotificationKey(sbn.key)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // eigene Notifications nicht spiegeln
        if (NotificationParser.shouldIgnore(sbn)) return

        val label = appLabel(sbn.packageName)
        val incoming = NotificationParser.parse(sbn, label) ?: return

        scope.launch {
            runCatching {
                // Quelle registrieren, damit sie in den Einstellungen auftaucht und
                // (de-)aktivierbar wird. Vorhandene Nutzer-Einstellungen bleiben erhalten.
                repository.registerSource(
                    SourceAppEntity(
                        sourceKey = incoming.sourceKey,
                        label = label,
                        packageName = sbn.packageName,
                        enabled = true,
                        isNativeConnector = false
                    )
                )
                if (!repository.isSourceEnabled(incoming.sourceKey)) return@runCatching

                repository.ingest(withAttachments(sbn, incoming))

                QuickReplyRegistry.findRemoteInputAction(sbn.notification)?.let { candidate ->
                    QuickReplyRegistry.register(
                        incoming.stableId,
                        QuickReplyRegistry.ReplyAction(
                            pendingIntent = candidate.pendingIntent,
                            remoteInputs = candidate.remoteInputs,
                            replyRemoteInput = candidate.replyRemoteInput,
                            notificationKey = sbn.key
                        )
                    )
                }
            }.onFailure { Log.w(TAG, "Notification konnte nicht verarbeitet werden", it) }
        }
    }

    /**
     * Extrahiert Bild-/Audioanhänge der Benachrichtigung, kopiert sie lokal und hängt die
     * lokalen URIs an die Nachricht. Läuft bereits im IO-Kontext (Aufrufer ist eine
     * IO-Coroutine), daher ist der Datei-/ContentResolver-Zugriff hier zulässig.
     */
    private fun withAttachments(sbn: StatusBarNotification, incoming: com.hub.app.data.source.IncomingMessage): com.hub.app.data.source.IncomingMessage {
        val raw = NotificationParser.extractAttachments(sbn)
        var imageUri: String? = null
        var audioUri: String? = null

        if (raw.picture != null) {
            imageUri = attachmentStore.saveBitmap(incoming.stableId, raw.picture)
        }
        val uri = raw.dataUri
        val mime = raw.dataMime
        if (uri != null && mime != null) {
            val ext = mime.substringAfter('/', "").ifBlank { if (mime.startsWith("audio")) "audio" else "img" }
            when {
                mime.startsWith("image/") ->
                    imageUri = imageUri ?: attachmentStore.saveFromUri(incoming.stableId, uri, isAudio = false, extension = ext)
                mime.startsWith("audio/") ->
                    audioUri = attachmentStore.saveFromUri(incoming.stableId, uri, isAudio = true, extension = ext)
            }
        }

        return if (imageUri == null && audioUri == null) incoming
        else incoming.copy(imageUri = imageUri, audioUri = audioUri)
    }

    private fun appLabel(packageName: String): String = appLabelCache.getOrPut(packageName) {
        runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
        }.getOrDefault(packageName)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HubNotifListener"

        /**
         * Ob das System den Listener aktuell gebunden hat. Nur ein Hinweis für die UI –
         * die verlässliche Prüfung, ob die Berechtigung erteilt wurde, ist
         * [NotificationAccess.isGranted].
         */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
