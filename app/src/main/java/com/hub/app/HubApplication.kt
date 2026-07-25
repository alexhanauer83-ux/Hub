package com.hub.app

import android.app.Application
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.di.ServiceLocator

class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startConfiguredConnectors()
    }

    /**
     * Startet API-Connectoren, die der Nutzer bereits eingerichtet hat.
     *
     * Bewusst nur beim App-Start: Die Polling-Schleifen laufen im Prozess der App. Sobald
     * Android den Prozess beendet, enden sie – für echten Hintergrundempfang wäre ein
     * Foreground-Service oder WorkManager nötig. Der Notification-Listener ist davon
     * nicht betroffen, den hält das System selbst am Leben.
     */
    private fun startConfiguredConnectors() {
        val registry = ServiceLocator.connectorRegistry(this)
        if (ServiceLocator.telegramConnector(this).isConfigured()) {
            registry.start(TelegramBotConnector.SOURCE_KEY)
        }
    }
}
