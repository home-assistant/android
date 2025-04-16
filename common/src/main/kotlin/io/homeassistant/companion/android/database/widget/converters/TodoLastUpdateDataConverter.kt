package io.homeassistant.companion.android.database.widget.converters

import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity

class TodoLastUpdateDataConverter {
    @TypeConverter
    fun fromJson(value: String?): TodoWidgetEntity.LastUpdateData? {
        val objectMapper = jacksonObjectMapper()
        return value?.let { objectMapper.readValue(it, TodoWidgetEntity.LastUpdateData::class.java) }
    }

    @TypeConverter
    fun toJson(data: TodoWidgetEntity.LastUpdateData?): String? {
        val objectMapper = jacksonObjectMapper()
        return data?.let { objectMapper.writeValueAsString(it) }
    }
}
