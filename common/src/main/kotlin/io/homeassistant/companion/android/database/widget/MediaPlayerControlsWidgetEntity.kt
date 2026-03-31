package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_player_controls_widgets")
data class MediaPlayerControlsWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "show_skip")
    val showSkip: Boolean,
    @ColumnInfo(name = "show_seek")
    val showSeek: Boolean,
    @ColumnInfo(name = "show_volume")
    val showVolume: Boolean,
    @ColumnInfo(name = "show_source", defaultValue = "false")
    val showSource: Boolean,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
) : WidgetEntity<MediaPlayerControlsWidgetEntity>,
    ThemeableWidgetEntity {
    override fun copyWithWidgetId(appWidgetId: Int): MediaPlayerControlsWidgetEntity {
        return copy(id = appWidgetId)
    }
}
