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
    /**
     * Icon ID provided by the `com.maltaisn:icondialog` library.
     * Convert to regular icon data using [io.homeassistant.companion.android.themes.icons.IconDialogCompat.getDialogIconMeta].
     */
    @ColumnInfo(name = "icon_id")
    val dialogIconId: Int?,
    @ColumnInfo(name = "entityId")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?
)

val TileEntity.isSetup: Boolean
    get() = this.label.isNotBlank() && this.entityId.isNotBlank()
