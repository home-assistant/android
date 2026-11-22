package io.homeassistant.companion.android.database.widget

import androidx.room3.ColumnTypeConverter

interface ThemeableWidgetEntity {
    val backgroundType: WidgetBackgroundType
    val textColor: String?
}

enum class WidgetBackgroundType {
    DYNAMICCOLOR,
    DAYNIGHT,
    TRANSPARENT,
}

class WidgetBackgroundColumnTypeConverter {
    @ColumnTypeConverter
    fun toWidgetBackgroundType(setting: String): WidgetBackgroundType = WidgetBackgroundType.valueOf(setting)

    @ColumnTypeConverter
    fun fromWidgetBackgroundType(setting: WidgetBackgroundType): String = setting.name
}
