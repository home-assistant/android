package io.homeassistant.companion.android.database.sensor

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import kotlin.enums.enumEntries

enum class SensorSettingType(val string: String, val listType: Boolean = false) {
    STRING("string"),
    NUMBER("number"),
    TOGGLE("toggle"),
    LIST("list", listType = true),
    LIST_APPS("list-apps", listType = true),
    LIST_BLUETOOTH("list-bluetooth", listType = true),
    LIST_ZONES("list-zones", listType = true),
    LIST_BEACONS("list-beacons", listType = true),
    ;

    fun isMultiSelect(): Boolean = listType && this != LIST
}

@Entity(tableName = "sensor_settings", primaryKeys = ["sensor_id", "name"])
data class SensorSetting(
    @ColumnInfo(name = "sensor_id")
    val sensorId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "value")
    val value: String,
    /** Indicates the data type of the `value`. */
    @ColumnInfo(name = "value_type")
    val valueType: SensorSettingType,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,
    @ColumnInfo(name = "entries")
    val entries: List<String> = arrayListOf(),
)

class EntriesColumnTypeConverter {
    @ColumnTypeConverter
    fun fromStringToList(value: String): List<String> {
        return value.split("|")
    }

    @ColumnTypeConverter
    fun toStringFromList(list: List<String>): String {
        return list.joinToString(separator = "|")
    }
}

class SensorSettingColumnTypeConverter {
    @ColumnTypeConverter
    fun fromStringToEnum(value: String): SensorSettingType {
        return enumEntries<SensorSettingType>().find { it.string == value }
            ?: SensorSettingType.STRING
    }

    @ColumnTypeConverter
    fun toStringFromEnum(enum: SensorSettingType): String {
        return enum.string
    }
}
