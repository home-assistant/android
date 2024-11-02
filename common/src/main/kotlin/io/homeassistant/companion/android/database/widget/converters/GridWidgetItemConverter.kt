package io.homeassistant.companion.android.database.widget.converters

import androidx.room.TypeConverter
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.GridWidgetEntity

class GridWidgetItemConverter {
    @TypeConverter
    fun fromJson(value: String?): List<GridWidgetEntity.Item>? {
        return value?.let { kotlinJsonMapper.decodeFromString<List<GridWidgetEntity.Item>>(it) }
    }

    @TypeConverter
    fun toJson(data: List<GridWidgetEntity.Item>?): String? {
        return data?.let { kotlinJsonMapper.encodeToString(it) }
    }
}
