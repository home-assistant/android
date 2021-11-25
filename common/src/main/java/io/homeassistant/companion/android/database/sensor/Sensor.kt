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
    @ColumnInfo(name = "state")
    var state: String,
    @ColumnInfo(name = "last_sent_state")
    var lastSentState: String = "",
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
    var unitOfMeasurement: String? = null,
    @ColumnInfo(name = "state_class")
    var stateClass: String? = null,
    @ColumnInfo(name = "entity_category")
    var entityCategory: String? = null,
    @ColumnInfo(name = "core_registration")
    var coreRegistration: String? = null,
    @ColumnInfo(name = "app_registration")
    var appRegistration: String? = null

)
