package io.homeassistant.companion.android.database.qs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qs_tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "tileId")
    val tileId: String,
    @ColumnInfo(name = "added", defaultValue = "1")
    val added: Boolean,
    @ColumnInfo(name = "icon_id")
    val iconId: Int?,
    @ColumnInfo(name = "entityId")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?,
    @ColumnInfo(name = "shouldVibrate", defaultValue = "0")
    val shouldVibrate: Boolean
)

val TileEntity.isSetup: Boolean
    get() = this.label.isNotBlank() && this.entityId.isNotBlank()
