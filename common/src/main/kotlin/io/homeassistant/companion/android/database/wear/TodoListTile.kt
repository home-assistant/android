package io.homeassistant.companion.android.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Represents the configuration of a to-do list tile.
 * If the tile was added but not configured, everything except the tile ID will be `null`.
 */
@Entity(tableName = "todo_list_tiles")
data class TodoListTile(
    /** The system's tile ID */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    /** The to-do entity ID */
    @ColumnInfo(name = "entity_id")
    val entityId: String? = null,
    /** The refresh interval of this tile, in seconds */
    @ColumnInfo(name = "refresh_interval")
    val refreshInterval: Long? = null,
)
