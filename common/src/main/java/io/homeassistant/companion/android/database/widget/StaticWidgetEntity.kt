package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "static_widget")
data class StaticWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "attribute_ids")
    val attributeIds: String?,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "text_size")
    val textSize: Float = 30F,
    @ColumnInfo(name = "state_separator")
    val stateSeparator: String = "",
    @ColumnInfo(name = "attribute_separator")
    val attributeSeparator: String = "",
    @ColumnInfo(name = "last_update")
    val lastUpdate: String
)
