package com.hub.app

import android.app.Application
import com.hub.app.connectors.ConnectorSyncService

class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Startet den Foreground-Service, der die API-Connectoren im Hintergrund am Leben
        // hält (nur wenn ein Connector eingerichtet und Hintergrund-Empfang aktiv ist).
        // Der Notification-Listener braucht das nicht – den hält das System selbst.
        ConnectorSyncService.startIfEnabled(this)
    }
}
