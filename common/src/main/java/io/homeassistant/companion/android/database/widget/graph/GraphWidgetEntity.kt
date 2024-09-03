package io.homeassistant.companion.android.database.widget.graph

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.homeassistant.companion.android.database.widget.ThemeableWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction

@Entity(tableName = "graph_widget")
data class GraphWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
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
    @ColumnInfo(name = "tap_action", defaultValue = "REFRESH")
    val tapAction: WidgetTapAction,
    @ColumnInfo(name = "last_update")
    val lastUpdate: String,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null
) : WidgetEntity, ThemeableWidgetEntity
