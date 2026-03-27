package io.homeassistant.companion.android.database.mediacontrol

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Stores a single `media_player` entity configured to be exposed as a native media control. */
@Entity(tableName = "media_control_entity_config")
data class MediaControlConfig(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "position")
    val position: Int,
)
