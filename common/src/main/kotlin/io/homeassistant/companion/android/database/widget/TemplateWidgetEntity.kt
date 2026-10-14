package io.homeassistant.companion.android.database.widget

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
    @ColumnInfo(name = "tap_action", defaultValue = "REFRESH")
    val tapAction: WidgetTapAction = WidgetTapAction.REFRESH,
    /** URL path of the dashboard opened by [WidgetTapAction.OPEN_DASHBOARD], without the leading `/`. */
    @ColumnInfo(name = "dashboard_path")
    val dashboardPath: String? = null,
) : WidgetEntity<TemplateWidgetEntity>,
    ThemeableWidgetEntity {
    override fun copyWithWidgetId(appWidgetId: Int): TemplateWidgetEntity {
        return copy(id = appWidgetId)
    }
}
