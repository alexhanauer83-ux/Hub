package com.hub.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.hub.app.data.local.entity.PriorityContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriorityContactDao {

    @Insert
    suspend fun insert(contact: PriorityContactEntity)

    @Delete
    suspend fun delete(contact: PriorityContactEntity)

    @Query("SELECT * FROM priority_contacts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PriorityContactEntity>>

    /** Ob dieser Absender in dieser Quelle als priorisierter Kontakt geführt wird (case-insensitive). */
    @Query(
        "SELECT COUNT(*) > 0 FROM priority_contacts " +
            "WHERE sourceKey = :sourceKey AND LOWER(senderMatch) = LOWER(:sender)"
    )
    suspend fun matches(sourceKey: String, sender: String): Boolean
}
