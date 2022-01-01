package io.homeassistant.companion.android.database.settings

import androidx.room.TypeConverter

enum class LocalNotificationSetting {
    NEVER,
    SCREEN_ON,
    ALWAYS
}

class LocalNotificationSettingConverter {
    @TypeConverter
    fun toLocalNotificationSetting(setting: String): LocalNotificationSetting = LocalNotificationSetting.valueOf(setting)
    @TypeConverter
    fun fromLocalNotificationSetting(setting: LocalNotificationSetting): String = setting.name
}
