package com.hub.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hub.app.data.local.dao.MessageDao
import com.hub.app.data.local.dao.PriorityContactDao
import com.hub.app.data.local.dao.SourceAppDao
import com.hub.app.data.local.entity.MessageEntity
import com.hub.app.data.local.entity.PriorityContactEntity
import com.hub.app.data.local.entity.SourceAppEntity

/**
 * Ab Phase 7 wird diese Datenbank per SQLCipher verschlüsselt geöffnet
 * (siehe [com.hub.app.security.DatabaseKeyManager] und den `SupportFactory`
 * in [com.hub.app.HubApplication]). Der Datenbank-Code selbst bleibt davon
 * unberührt, da SQLCipher rein auf Ebene des `SupportSQLiteOpenHelper.Factory`
 * ansetzt.
 */
@Database(
    entities = [MessageEntity::class, SourceAppEntity::class, PriorityContactEntity::class],
    // v2: imageUri/audioUri in MessageEntity. Migration ist destruktiv
    // (fallbackToDestructiveMigration), da die DB nur ein lokaler Nachrichten-Cache ist.
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HubDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sourceAppDao(): SourceAppDao
    abstract fun priorityContactDao(): PriorityContactDao

    companion object {
        const val DATABASE_NAME = "hub.db"
    }
}
