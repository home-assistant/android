package io.homeassistant.companion.android.database.wear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the configuration of an entity state complication
 */
@Entity(tableName = "entity_state_complications")
data class EntityStateComplications(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "show_title", defaultValue = "1")
    val showTitle: Boolean,
    @ColumnInfo(name = "show_unit", defaultValue = "0")
    val showUnit: Boolean,
    @ColumnInfo(name = "forward_taps", defaultValue = "0")
    val forwardTaps: Boolean
)
