package io.homeassistant.companion.android.database.widget.converters

import androidx.room.TypeConverter
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity

class TodoLastUpdateDataConverter {
    @TypeConverter
    fun fromJson(value: String?): TodoWidgetEntity.LastUpdateData? {
        return value?.let { kotlinJsonMapper.decodeFromString<TodoWidgetEntity.LastUpdateData>(it) }
    }

    @TypeConverter
    fun toJson(data: TodoWidgetEntity.LastUpdateData?): String? {
        return data?.let { kotlinJsonMapper.encodeToString(it) }
    }
}
