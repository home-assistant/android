package io.homeassistant.companion.android.database.location

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_history")
data class LocationHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "created")
    val created: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "trigger")
    val trigger: LocationHistoryItemTrigger,
    @ColumnInfo(name = "result")
    val result: LocationHistoryItemResult,
    @ColumnInfo(name = "latitude")
    val latitude: Double?,
    @ColumnInfo(name = "longitude")
    val longitude: Double?,
    @ColumnInfo(name = "location_name")
    val locationName: String?,
    @ColumnInfo(name = "accuracy")
    val accuracy: Int?,
    @ColumnInfo(name = "data")
    val data: String?,
    @ColumnInfo(name = "server_id")
    val serverId: Int?
)

enum class LocationHistoryItemTrigger {
    FLP_BACKGROUND,
    FLP_FOREGROUND,
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    GEOFENCE_DWELL,
    SINGLE_ACCURATE_LOCATION,
    UNKNOWN
}

enum class LocationHistoryItemResult {
    SKIPPED_ACCURACY,
    SKIPPED_FUTURE,
    SKIPPED_NOT_LATEST,
    SKIPPED_DUPLICATE,
    SKIPPED_DEBOUNCE,
    SKIPPED_OLD,
    SENT
}
