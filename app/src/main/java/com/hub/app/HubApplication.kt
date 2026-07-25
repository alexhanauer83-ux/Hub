package com.hub.app

import android.app.Application

class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Absichtlich schlank: DB/Repository werden lazy über ServiceLocator erzeugt,
        // sobald die erste Komponente (Activity oder NotificationListenerService) sie braucht.
    }
}
