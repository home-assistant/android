package io.homeassistant.companion.android.database.widget

import androidx.room3.ColumnTypeConverter

/** What happens when the user taps a widget. Each widget only offers the actions that apply to it. */
enum class WidgetTapAction {
    REFRESH,
    OPEN,
    TOGGLE,

    /** Open the dashboard stored with the widget, used by the template widget. */
    OPEN_DASHBOARD,
}

class WidgetTapActionColumnTypeConverter {

    @ColumnTypeConverter
    fun toWidgetTapAction(setting: String): WidgetTapAction = WidgetTapAction.valueOf(setting)

    @ColumnTypeConverter
    fun fromWidgetBackgroundType(setting: WidgetTapAction): String = setting.name
}
