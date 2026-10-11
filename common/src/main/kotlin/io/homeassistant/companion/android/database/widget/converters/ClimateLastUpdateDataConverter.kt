package io.homeassistant.companion.android.database.widget.converters

import androidx.room3.ColumnTypeConverter
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity

class ClimateLastUpdateDataConverter {
    @ColumnTypeConverter
    fun fromJson(value: String?): ClimateWidgetEntity.LastUpdateData? {
        return value?.let { kotlinJsonMapper.decodeFromString<ClimateWidgetEntity.LastUpdateData>(it) }
    }

    @ColumnTypeConverter
    fun toJson(data: ClimateWidgetEntity.LastUpdateData?): String? {
        return data?.let { kotlinJsonMapper.encodeToString(it) }
    }
}
