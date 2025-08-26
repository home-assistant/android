package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "template_widgets")
data class TemplateWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "template")
    val template: String,
    @ColumnInfo(name = "text_size", defaultValue = "12.0")
    val textSize: Float,
    @ColumnInfo(name = "last_update")
    val lastUpdate: String,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
) : WidgetEntity<TemplateWidgetEntity>,
    ThemeableWidgetEntity {
    override fun copyWithWidgetId(appWidgetId: Int): TemplateWidgetEntity {
        return copy(id = appWidgetId)
    }
}
