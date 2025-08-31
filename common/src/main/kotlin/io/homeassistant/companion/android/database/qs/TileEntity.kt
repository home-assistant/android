package io.homeassistant.companion.android.database.qs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qs_tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "tile_id")
    val tileId: String,
    @ColumnInfo(name = "added", defaultValue = "1")
    val added: Boolean,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    val serverId: Int,
    /** Icon name, such as "mdi:account-alert" */
    @ColumnInfo(name = "icon_name")
    val iconName: String?,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?,
    @ColumnInfo(name = "should_vibrate", defaultValue = "0")
    val shouldVibrate: Boolean,
    @ColumnInfo(name = "auth_required", defaultValue = "0")
    val authRequired: Boolean,
)

val TileEntity.isSetup: Boolean
    get() = this.label.isNotBlank() && this.entityId.isNotBlank()

val TileEntity.numberedId: Int
    get() = this.tileId.split("_")[1].toInt()
