package io.homeassistant.companion.android.database.sensor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverter

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
    @ColumnInfo(name = "entries")
    var entries: List<String> = arrayListOf(),
    @ColumnInfo(name = "enabled")
    var enabled: Boolean = true
) {

    constructor(sensorId: String, name: String, value: String, valueType: String, enabled: Boolean) : this(sensorId, name, value, valueType, arrayListOf(), enabled)
}

class EntriesTypeConverter {
    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        return value.split("|").map { it }
    }

    @TypeConverter
    fun toStringFromList(list: List<String>): String {
        return list.joinToString(separator = "|")
    }
}
