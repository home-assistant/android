package io.homeassistant.companion.android.database.widget

import androidx.room.TypeConverter

enum class WidgetTapAction {
    REFRESH, TOGGLE
}

class WidgetTapActionConverter {

    @TypeConverter
    fun toWidgetTapAction(setting: String): WidgetTapAction = WidgetTapAction.valueOf(setting)

    @TypeConverter
    fun fromWidgetBackgroundType(setting: WidgetTapAction): String = setting.name
}
