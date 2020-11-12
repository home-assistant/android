package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementEntity

@Entity(tableName = "multi_widgets")
data class MultiWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "elements")
    val elements: List<MultiWidgetElementEntity>
)
