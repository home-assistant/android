package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_widget")
data class TodoWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
    @ColumnInfo(name = "show_completed", defaultValue = "true")
    val showCompleted: Boolean = true
) : WidgetEntity, ThemeableWidgetEntity
