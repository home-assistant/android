package io.homeassistant.companion.android.database.widget.converters

import androidx.room3.ColumnTypeConverter
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity

class TodoLastUpdateDataConverter {
    @ColumnTypeConverter
    fun fromJson(value: String?): TodoWidgetEntity.LastUpdateData? {
        return value?.let { kotlinJsonMapper.decodeFromString<TodoWidgetEntity.LastUpdateData>(it) }
    }

    @ColumnTypeConverter
    fun toJson(data: TodoWidgetEntity.LastUpdateData?): String? {
        return data?.let { kotlinJsonMapper.encodeToString(it) }
    }
}
