package io.homeassistant.companion.android.database.settings

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "websocketSetting")
    var websocketSetting: WebsocketSetting,
    @ColumnInfo(name = "sensorUpdateFrequency")
    var sensorUpdateFrequency: SensorUpdateFrequencySetting
)
