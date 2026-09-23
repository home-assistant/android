package io.homeassistant.companion.android.database.mediacontrol

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Stores a single `media_player` entity configured to be exposed as a native media control. */
@Entity(tableName = "media_controls_entity_config", primaryKeys = ["server_id", "entity_id"])
data class MediaControlsConfig(
    @ColumnInfo(name = "server_id")
    val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
)
