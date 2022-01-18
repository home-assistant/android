package io.homeassistant.companion.android.database.settings

import androidx.room.TypeConverter

enum class WebsocketSetting {
    NEVER,
    SCREEN_ON,
    ALWAYS
}

class LocalNotificationSettingConverter {
    @TypeConverter
    fun toLocalNotificationSetting(setting: String): WebsocketSetting = WebsocketSetting.valueOf(setting)
    @TypeConverter
    fun fromLocalNotificationSetting(setting: WebsocketSetting): String = setting.name
}
