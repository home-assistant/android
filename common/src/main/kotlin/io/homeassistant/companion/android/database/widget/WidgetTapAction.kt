package io.homeassistant.companion.android.database.widget

import androidx.room3.ColumnTypeConverter

enum class WidgetTapAction {
    REFRESH,
    OPEN,
    TOGGLE,
}

class WidgetTapActionColumnTypeConverter {

    @ColumnTypeConverter
    fun toWidgetTapAction(setting: String): WidgetTapAction = WidgetTapAction.valueOf(setting)

    @ColumnTypeConverter
    fun fromWidgetBackgroundType(setting: WidgetTapAction): String = setting.name
}
