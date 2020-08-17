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
    @ColumnInfo(name = "attribute_id")
    val attributeId: String?,
    @ColumnInfo(name = "label")
    val label: String?
)
