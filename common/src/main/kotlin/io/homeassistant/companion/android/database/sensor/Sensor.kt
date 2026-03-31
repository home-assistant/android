package io.homeassistant.companion.android.database.sensor

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "sensors", primaryKeys = ["id", "server_id"])
data class Sensor(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    val serverId: Int,
    @ColumnInfo(name = "enabled")
    var enabled: Boolean,
    @ColumnInfo(name = "registered", defaultValue = "NULL")
    var registered: Boolean? = null,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "last_sent_state", defaultValue = "NULL")
    var lastSentState: String? = null,
    @ColumnInfo(name = "last_sent_icon", defaultValue = "NULL")
    var lastSentIcon: String? = null,
    @ColumnInfo(name = "state_type")
    val stateType: String = "",
    @ColumnInfo(name = "type")
    val type: String = "",
    @ColumnInfo(name = "icon")
    val icon: String = "",
    @ColumnInfo(name = "name")
    val name: String = "",
    @ColumnInfo(name = "device_class")
    val deviceClass: String? = null,
    @ColumnInfo(name = "unit_of_measurement")
    val unitOfMeasurement: String? = null,
    @ColumnInfo(name = "state_class")
    val stateClass: String? = null,
    @ColumnInfo(name = "entity_category")
    val entityCategory: String? = null,
    @ColumnInfo(name = "core_registration")
    var coreRegistration: String? = null,
    @ColumnInfo(name = "app_registration")
    var appRegistration: String? = null,
)
