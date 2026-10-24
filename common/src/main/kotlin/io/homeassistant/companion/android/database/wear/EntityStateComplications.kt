package io.homeassistant.companion.android.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
)
