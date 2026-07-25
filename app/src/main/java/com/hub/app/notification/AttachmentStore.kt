package com.hub.app.notification

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Persistiert Anhänge (Bilder/Audio) aus Benachrichtigungen lokal, damit sie im Hub-Feed
 * auch dann noch verfügbar sind, wenn die Original-Benachrichtigung längst verschwunden ist.
 *
 * Warum lokal kopieren: Content-URIs aus fremden Benachrichtigungen sind nur so lange gültig,
 * wie die Notification lebt (temporäre URI-Permission). Danach ist die URI wertlos. Deshalb
 * kopieren wir den Inhalt sofort in den privaten Speicher der App und referenzieren nur noch
 * diese lokale Kopie. Alles bleibt damit auf dem Gerät.
 *
 * Der Dateiname leitet sich deterministisch aus der stableId ab, sodass erneutes Einlesen
 * derselben Nachricht keine Duplikate erzeugt.
 */
class AttachmentStore(context: Context) {

    private val imageDir = File(context.filesDir, "attachments/images").apply { mkdirs() }
    private val audioDir = File(context.filesDir, "attachments/audio").apply { mkdirs() }
    private val resolver = context.contentResolver

    /** Speichert eine Bitmap (z. B. aus EXTRA_PICTURE) als PNG und liefert die file://-URI. */
    fun saveBitmap(stableId: String, bitmap: Bitmap): String? = runCatching {
        val file = File(imageDir, "${hash(stableId)}.png")
        if (!file.exists()) {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        Uri.fromFile(file).toString()
    }.onFailure { Log.w(TAG, "Bild konnte nicht gespeichert werden", it) }.getOrNull()

    /**
     * Kopiert den Inhalt einer Content-URI (Bild oder Audio aus einer MessagingStyle-
     * Nachricht) in den lokalen Speicher. Schlägt still fehl, wenn keine Leseberechtigung
     * für die URI vorliegt – das ist bei fremden Apps möglich und kein Fehler der App.
     */
    fun saveFromUri(stableId: String, uri: Uri, isAudio: Boolean, extension: String): String? =
        runCatching {
            val dir = if (isAudio) audioDir else imageDir
            val file = File(dir, "${hash(stableId)}.$extension")
            if (!file.exists()) {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: return null
            }
            Uri.fromFile(file).toString()
        }.onFailure { Log.w(TAG, "Anhang konnte nicht kopiert werden ($uri)", it) }.getOrNull()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    companion object {
        private const val TAG = "AttachmentStore"
    }
}
