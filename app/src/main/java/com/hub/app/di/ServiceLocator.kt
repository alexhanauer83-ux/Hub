package com.hub.app.di

import android.content.Context
import androidx.room.Room
import com.hub.app.connectors.ConnectorRegistry
import com.hub.app.connectors.telegram.TelegramBotConnector
import com.hub.app.data.local.HubDatabase
import com.hub.app.data.repository.MessageRepository
import com.hub.app.security.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

/**
 * Bewusst kein DI-Framework (Hilt/Koin): Die App hat wenige, klar geschnittene
 * Singletons (DB, Repository), ein manueller Locator hält das nachvollziehbar und
 * dependency-frei. `NotificationListenerService` & Co. werden vom System instanziiert
 * und brauchen ohnehin einen statischen Zugriffspunkt.
 */
object ServiceLocator {

    @Volatile private var database: HubDatabase? = null
    @Volatile private var messageRepository: MessageRepository? = null
    @Volatile private var connectorRegistry: ConnectorRegistry? = null
    @Volatile private var telegramConnector: TelegramBotConnector? = null

    fun telegramConnector(context: Context): TelegramBotConnector =
        telegramConnector ?: synchronized(this) {
            telegramConnector ?: TelegramBotConnector(context.applicationContext)
                .also { telegramConnector = it }
        }

    /**
     * Registriert alle API-Connectoren. Der Notification-Listener taucht hier bewusst
     * nicht auf: Er wird vom System gebunden und startet nicht über die Registry.
     */
    fun connectorRegistry(context: Context): ConnectorRegistry =
        connectorRegistry ?: synchronized(this) {
            connectorRegistry ?: ConnectorRegistry(messageRepository(context)).apply {
                register(telegramConnector(context))
                // IMAP und Matrix sind noch Stubs (siehe deren KDoc) und werden daher
                // nicht registriert - sonst wuerden sie in den Einstellungen als
                // nutzbare Quellen erscheinen, ohne es zu sein.
            }.also { connectorRegistry = it }
        }

    fun database(context: Context): HubDatabase =
        database ?: synchronized(this) {
            database ?: buildDatabase(context.applicationContext).also { database = it }
        }

    fun messageRepository(context: Context): MessageRepository =
        messageRepository ?: synchronized(this) {
            messageRepository ?: run {
                val db = database(context)
                MessageRepository(db.messageDao(), db.sourceAppDao(), db.priorityContactDao()).also {
                    messageRepository = it
                }
            }
        }

    /**
     * Öffnet die Datenbank SQLCipher-verschlüsselt. Der Schlüssel kommt aus dem
     * Keystore-gesicherten [DatabaseKeyManager]; SQLCipher überschreibt das übergebene
     * ByteArray nach dem Öffnen selbst.
     */
    private fun buildDatabase(context: Context): HubDatabase {
        val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
        return Room.databaseBuilder(context, HubDatabase::class.java, HubDatabase.DATABASE_NAME)
            .openHelperFactory(SupportFactory(passphrase))
            // Die Datenbank ist ein lokaler Cache abgegriffener Nachrichten, keine
            // Primaerquelle - bei einem Schema-Sprung ist Neuaufbau vertretbar und
            // allemal besser als eine fehlerhafte Migration ueber verschluesselten Daten.
            .fallbackToDestructiveMigration()
            .build()
    }
}
