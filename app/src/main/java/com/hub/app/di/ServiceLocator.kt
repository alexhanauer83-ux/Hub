package com.hub.app.di

import android.content.Context
import androidx.room.Room
import com.hub.app.data.local.HubDatabase
import com.hub.app.data.repository.MessageRepository

/**
 * Bewusst kein DI-Framework (Hilt/Koin): Die App hat wenige, klar geschnittene
 * Singletons (DB, Repository), ein manueller Locator hält das nachvollziehbar und
 * dependency-frei. `NotificationListenerService` & Co. werden vom System instanziiert
 * und brauchen ohnehin einen statischen Zugriffspunkt.
 */
object ServiceLocator {

    @Volatile private var database: HubDatabase? = null
    @Volatile private var messageRepository: MessageRepository? = null

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

    private fun buildDatabase(context: Context): HubDatabase =
        Room.databaseBuilder(context, HubDatabase::class.java, HubDatabase.DATABASE_NAME)
            // Ab Phase 7 hier .openHelperFactory(SupportFactory(passphrase)) für SQLCipher.
            .fallbackToDestructiveMigration()
            .build()
}
