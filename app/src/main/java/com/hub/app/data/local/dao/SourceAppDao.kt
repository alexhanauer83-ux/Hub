package com.hub.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hub.app.data.local.entity.SourceAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceAppDao {

    @Upsert
    suspend fun upsert(sourceApp: SourceAppEntity)

    @Query("SELECT * FROM source_apps ORDER BY label ASC")
    fun observeAll(): Flow<List<SourceAppEntity>>

    @Query("SELECT * FROM source_apps WHERE sourceKey = :sourceKey")
    suspend fun getBySourceKey(sourceKey: String): SourceAppEntity?

    @Query("UPDATE source_apps SET enabled = :enabled WHERE sourceKey = :sourceKey")
    suspend fun setEnabled(sourceKey: String, enabled: Boolean)

    @Query("UPDATE source_apps SET isPriority = :isPriority WHERE sourceKey = :sourceKey")
    suspend fun setPriority(sourceKey: String, isPriority: Boolean)

    @Query("SELECT enabled FROM source_apps WHERE sourceKey = :sourceKey")
    suspend fun isEnabled(sourceKey: String): Boolean?
}
