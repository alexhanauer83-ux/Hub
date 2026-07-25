package com.hub.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Priorisierung eines einzelnen Absenders innerhalb einer Quelle (feiner als
 * [SourceAppEntity.isPriority], das eine ganze App priorisiert). `senderMatch` wird
 * gegen [MessageEntity.sender] verglichen (exakter String-Vergleich, case-insensitive).
 */
@Entity(tableName = "priority_contacts")
data class PriorityContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val senderMatch: String,
    val createdAt: Long
)
