package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grid_widget_items")
data class GridWidgetItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "grid_id")
    val gridId: Int,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "service")
    val service: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "icon_name")
    val iconName: String
)
