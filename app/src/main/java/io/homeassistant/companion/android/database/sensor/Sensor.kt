package io.homeassistant.companion.android.database.sensor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensors")
data class Sensor(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: String,
    @ColumnInfo(name = "enabled")
    var enabled: Boolean,
    @ColumnInfo(name = "registered")
    var registered: Boolean,
    @ColumnInfo(name = "state_changed")
    var stateChanged: Boolean,
    @ColumnInfo(name = "state")
    var state: String,
    @ColumnInfo(name = "state_type")
    var stateType: String = "",
    @ColumnInfo(name = "type")
    var type: String = "",
    @ColumnInfo(name = "icon")
    var icon: String = "",
    @ColumnInfo(name = "name")
    var name: String = "",
    @ColumnInfo(name = "device_class")
    var deviceClass: String? = null,
    @ColumnInfo(name = "unit_of_measurement")
    var unitOfMeasurement: String? = null

)
