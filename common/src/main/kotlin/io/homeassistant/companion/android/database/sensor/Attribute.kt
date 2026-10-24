package io.homeassistant.companion.android.database.sensor

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(tableName = "sensor_attributes", primaryKeys = ["sensor_id", "name"])
data class Attribute(
    @ColumnInfo(name = "sensor_id")
    val sensorId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "value_type")
    val valueType: String,
)
