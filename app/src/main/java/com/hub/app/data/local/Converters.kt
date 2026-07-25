package com.hub.app.data.local

import androidx.room.TypeConverter
import com.hub.app.data.local.entity.MessageCategory

class Converters {
    @TypeConverter
    fun fromCategory(category: MessageCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): MessageCategory =
        runCatching { MessageCategory.valueOf(value) }.getOrDefault(MessageCategory.OTHER)
}
