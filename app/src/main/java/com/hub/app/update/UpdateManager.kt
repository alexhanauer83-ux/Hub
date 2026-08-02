package com.hub.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Selbst-Update über GitHub Releases – ohne zweite App. Prüft das neueste Release des
 * Repos, vergleicht die Version mit der installierten und lädt/installiert bei Bedarf die APK.
 *
 * Bewusst schlank (OkHttp + Moshi, beides schon im Projekt). Der finale
 * Installationsdialog wird von Android erzwungen (Sideloading) – „vollständig
 * unbeobachtet" ist ohne Root/Shizuku nicht möglich.
 */
object UpdateManager {

    private const val OWNER = "alexhanauer83-ux"
    private const val REPO = "Hub"
    private const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val DOWNLOAD_DIR = "updates"
    private const val FILE_NAME = "hub-update.apk"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val KEY_DOWNLOAD_ID = "download_id"

    // Timeouts, damit ein Download bei schlechtem Netz sauber abbricht statt zu haengen.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().build()

    /** Darf Hub APKs installieren? (Android 8+ verlangt die Freigabe "Unbekannte Apps".) */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Öffnet die Systemeinstellung, um Hub die Installation unbekannter Apps zu erlauben. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    data class UpdateInfo(val versionName: String, val apkUrl: String, val notes: String?)

    /** Liefert Update-Infos, wenn online eine neuere Version vorliegt, sonst null. */
    suspend fun check(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub-Antwort ${response.code}")
                val body = response.body?.string() ?: throw IOException("Leere Antwort")
                val release = moshi.adapter(GhRelease::class.java).fromJson(body)
                    ?: throw IOException("Release nicht lesbar")

                val latest = release.tagName.removePrefix("v").trim()
                val current = currentVersion(context)
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                if (apk != null && isNewer(latest, current)) {
                    UpdateInfo(latest, apk.downloadUrl, release.body?.takeIf { it.isNotBlank() })
                } else {
                    null
                }
            }
        }
    }

    /**
     * Lädt die APK über den **System-DownloadManager** herunter. Der läuft im Hintergrund
     * weiter, auch wenn der Bildschirm gesperrt oder die App gewechselt wird, und hat kein
     * kurzes Timeout. Nach Abschluss startet [UpdateDownloadReceiver] die Installation.
     * Liefert die Download-ID.
     */
    fun enqueueDownload(context: Context, info: UpdateInfo): Long {
        val dm = context.getSystemService(android.app.DownloadManager::class.java)
        // Alte Datei entfernen, damit keine veraltete APK installiert wird.
        runCatching { downloadFile(context).delete() }

        val request = android.app.DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Hub-Update ${info.versionName}")
            .setDescription("Wird heruntergeladen …")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, DOWNLOAD_DIR, FILE_NAME)
            .setMimeType(APK_MIME)

        val id = dm.enqueue(request)
        prefs(context).edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun expectedDownloadId(context: Context): Long =
        prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)

    private fun downloadFile(context: Context): File =
        File(context.getExternalFilesDir(DOWNLOAD_DIR), FILE_NAME)

    /** Startet den System-Installer für die zuletzt heruntergeladene APK. */
    fun installDownloaded(context: Context) {
        val apk = downloadFile(context)
        if (!apk.exists()) return
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("hub_update", Context.MODE_PRIVATE)

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /** Semantischer Versionsvergleich: ist [latest] höher als [current]? */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val max = maxOf(l.size, c.size)
        for (i in 0 until max) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

@JsonClass(generateAdapter = true)
data class GhRelease(
    @Json(name = "tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GhAsset(
    val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String
)
