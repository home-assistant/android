package io.homeassistant.companion.android.database.qs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qs_tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    @ColumnInfo(name = "tileId")
    val tileId: String,
    @ColumnInfo(name = "icon_id")
    val iconId: Int?,
    @ColumnInfo(name = "entityId")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?
)
