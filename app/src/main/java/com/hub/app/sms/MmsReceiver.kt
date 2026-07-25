package com.hub.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Pflichtkomponente für die Standard-SMS-App-Rolle: Android verlangt einen Receiver für
 * `WAP_PUSH_DELIVER` (MMS), sonst taucht die App gar nicht erst in der Auswahlliste auf.
 *
 * MMS-Verarbeitung (Herunterladen des Inhalts über die MMSC, Multipart-Parsing) ist
 * bewusst **nicht** implementiert – das ist ein eigenständiges, umfangreiches Thema und
 * für den Hub-Anwendungsfall (Textnachrichten aggregieren) zweitrangig. Der Receiver
 * existiert, damit die Rolle überhaupt vergeben werden kann, und protokolliert nur.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "MMS empfangen, wird von Hub derzeit nicht verarbeitet: ${intent.action}")
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
