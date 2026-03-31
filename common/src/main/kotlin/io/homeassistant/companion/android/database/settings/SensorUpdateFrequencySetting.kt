package io.homeassistant.companion.android.database.settings

import androidx.room.TypeConverter

enum class SensorUpdateFrequencySetting {
    NORMAL,
    FAST_WHILE_CHARGING,
    FAST_ALWAYS,
}

class LocalSensorSettingConverter {
    @TypeConverter
    fun toLocalSensorSetting(setting: String): SensorUpdateFrequencySetting =
        SensorUpdateFrequencySetting.valueOf(setting)

    @TypeConverter
    fun fromLocalSensorSetting(setting: SensorUpdateFrequencySetting): String = setting.name
}
