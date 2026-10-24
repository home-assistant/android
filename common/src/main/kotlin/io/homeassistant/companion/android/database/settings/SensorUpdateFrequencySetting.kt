package io.homeassistant.companion.android.database.settings

import androidx.room3.ColumnTypeConverter

enum class SensorUpdateFrequencySetting {
    NORMAL,
    FAST_WHILE_CHARGING,
    FAST_ALWAYS,
}

class LocalSensorSettingColumnTypeConverter {
    @ColumnTypeConverter
    fun toLocalSensorSetting(setting: String): SensorUpdateFrequencySetting =
        SensorUpdateFrequencySetting.valueOf(setting)

    @ColumnTypeConverter
    fun fromLocalSensorSetting(setting: SensorUpdateFrequencySetting): String = setting.name
}
