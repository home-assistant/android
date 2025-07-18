package io.homeassistant.companion.android.database.settings

import androidx.room.TypeConverter

enum class PushProviderSetting {
    NONE,
    FCM
}

class LocalPushProviderSettingConverter {
    @TypeConverter
    fun toLocalPushProviderSetting(setting: String): PushProviderSetting = PushProviderSetting.valueOf(setting)

    @TypeConverter
    fun fromLocalPushProviderSetting(setting: PushProviderSetting): String = setting.name
}
