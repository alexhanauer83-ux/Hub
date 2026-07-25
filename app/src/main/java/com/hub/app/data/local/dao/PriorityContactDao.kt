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
}
