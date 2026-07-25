package com.hub.app

import android.app.Application
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        // Nicht auf dem Main-Thread: Der erste Zugriff initialisiert Keystore,
        // EncryptedSharedPreferences und die SQLCipher-Datenbank - alles Disk-I/O
        // plus Krypto, das den App-Start sichtbar verzoegern wuerde.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val registry = ServiceLocator.connectorRegistry(this@HubApplication)
            if (ServiceLocator.telegramConnector(this@HubApplication).isConfigured()) {
                registry.start(TelegramBotConnector.SOURCE_KEY)
            }
        }
    }
}
