package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.widget.converters.GridWidgetItemConverter
import kotlinx.serialization.Serializable

@TypeConverters(GridWidgetItemConverter::class)
@Entity(tableName = "grid_widgets")
data class GridWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "items")
    val items: List<Item>,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
) : WidgetEntity<GridWidgetEntity>,
    ThemeableWidgetEntity {

    @Serializable
    data class Item(val gridId: Int, val entityId: String, val label: String, val iconName: String) :
        java.io.Serializable

    override fun copyWithWidgetId(appWidgetId: Int): GridWidgetEntity = copy(id = appWidgetId)
}
