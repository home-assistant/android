package io.homeassistant.companion.android.database.settings

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "websocket_setting")
    val websocketSetting: WebsocketSetting,
    @ColumnInfo(name = "sensor_update_frequency")
    val sensorUpdateFrequency: SensorUpdateFrequencySetting,
)
