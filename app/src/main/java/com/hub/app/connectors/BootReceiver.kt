package com.hub.app.connectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Startet den [ConnectorSyncService] nach einem Geräteneustart wieder, damit der
 * Hintergrund-Empfang der API-Connectoren nicht erst beim nächsten App-Start anläuft.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ConnectorSyncService.startIfEnabled(context.applicationContext)
        }
    }
}
