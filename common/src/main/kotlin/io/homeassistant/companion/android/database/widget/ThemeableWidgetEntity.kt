package io.homeassistant.companion.android.database.widget

import androidx.room.TypeConverter

interface ThemeableWidgetEntity {
    val backgroundType: WidgetBackgroundType
    val textColor: String?
}

enum class WidgetBackgroundType {
    DYNAMICCOLOR,
    DAYNIGHT,
    TRANSPARENT,
}

class WidgetBackgroundTypeConverter {
    @TypeConverter
    fun toWidgetBackgroundType(setting: String): WidgetBackgroundType = WidgetBackgroundType.valueOf(setting)

    @TypeConverter
    fun fromWidgetBackgroundType(setting: WidgetBackgroundType): String = setting.name
}
