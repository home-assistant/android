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
    @ColumnInfo(name = "sensorUpdateFrequencyBattery")
    var sensorUpdateFrequencyBattery: Int, /* minutes */
    @ColumnInfo(name = "sensorUpdateFrequencyPowered")
    var sensorUpdateFrequencyPowered: Int /* minutes */
)

/**
 * Array containing the various update frequency options
 */
val UpdateFrequencies = intArrayOf(
    1, 2, 5, 10,             // Fast updates
    15, 20, 30, 45, 60,      // Normal updates
    60 * 2, 60 * 4, 60 * 12, // Slow updates
)
