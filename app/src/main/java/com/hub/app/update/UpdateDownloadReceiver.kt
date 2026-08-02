package com.hub.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Startet die Installation, sobald der [android.app.DownloadManager] das Hub-Update fertig
 * geladen hat. Weil der Download vom System läuft, überlebt er Bildschirmsperre und
 * App-Wechsel – der frühere Abbruch (Download im ViewModel-Scope) ist damit behoben.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L || id != UpdateManager.expectedDownloadId(context)) return

        val dm = context.getSystemService(DownloadManager::class.java) ?: return
        val successful = dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            status == DownloadManager.STATUS_SUCCESSFUL
        }

        if (successful) {
            UpdateManager.installDownloaded(context.applicationContext)
        }
    }
}
