package io.homeassistant.companion.android.database.sensor

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "sensor_settings", primaryKeys = ["sensor_id", "name"])
data class Setting(
    @ColumnInfo(name = "sensor_id")
    val sensorId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "value")
    var value: String,
    @ColumnInfo(name = "value_type")
    var valueType: String,
    @ColumnInfo(name = "enabled")
    var enabled: Boolean = true
)
