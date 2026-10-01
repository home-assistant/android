package io.homeassistant.companion.android.database.location

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.SerializationException

@Entity(tableName = "location_history")
data class LocationHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val created: Long = System.currentTimeMillis(),
    val trigger: LocationHistoryItemTrigger,
    val result: LocationHistoryItemResult,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "location_name")
    val locationName: String?,
    /**
     * Full zone entity IDs the device was in when this row was logged. Stored as a JSON string
     * via [LocationHistoryInZonesColumnTypeConverter]; empty list means the device was in no zone.
     */
    @ColumnInfo(name = "in_zones", defaultValue = "[]")
    @param:ColumnTypeConverters(LocationHistoryInZonesColumnTypeConverter::class)
    val inZones: List<String> = emptyList(),
    val accuracy: Int?,
    val data: String?,
    @ColumnInfo(name = "server_id")
    val serverId: Int?,
) {
    fun toSharingString(serverName: String?): String {
        val createdString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(created)
        val zonesLine = if (inZones.isNotEmpty()) "In zones: ${inZones.joinToString()}\n" else ""

        return "Created: $createdString\n" +
            "Trigger: $trigger\n" +
            "Result: $result\n" +
            "Data: $data\n\n" +
            "Location: ${locationName ?: "$latitude, $longitude"}\n" +
            zonesLine +
            "Accuracy: $accuracy\n" +
            "Server: ${if (serverId != null) serverName?.ifBlank { serverId } ?: serverId else "null"}"
    }
}

/**
 * Room [ColumnTypeConverter] for serializing the `in_zones` column (a list of zone entity IDs) to/from
 * JSON strings.
 */
class LocationHistoryInZonesColumnTypeConverter {
    @ColumnTypeConverter
    fun fromStringToList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            kotlinJsonMapper.decodeFromString(value)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    @ColumnTypeConverter
    fun fromListToString(value: List<String>): String = try {
        kotlinJsonMapper.encodeToString(value)
    } catch (_: SerializationException) {
        ""
    }
}

enum class LocationHistoryItemTrigger {
    FLP_BACKGROUND,
    FLP_FOREGROUND,
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    GEOFENCE_DWELL,
    SINGLE_ACCURATE_LOCATION,
    UNKNOWN,
}

enum class LocationHistoryItemResult {
    SKIPPED_ACCURACY,
    SKIPPED_FUTURE,
    SKIPPED_NOT_LATEST,
    SKIPPED_DUPLICATE,
    SKIPPED_DEBOUNCE,
    SKIPPED_OLD,
    FAILED_SEND,
    SENT,
}
