package io.homeassistant.companion.android.database.settings

import androidx.room3.ColumnTypeConverter

enum class WebsocketSetting {
    NEVER,
    SCREEN_ON,
    ALWAYS,
    HOME_WIFI,
}

class LocalNotificationSettingColumnTypeConverter {
    @ColumnTypeConverter
    fun toLocalNotificationSetting(setting: String): WebsocketSetting = WebsocketSetting.valueOf(setting)

    @ColumnTypeConverter
    fun fromLocalNotificationSetting(setting: WebsocketSetting): String = setting.name
}
