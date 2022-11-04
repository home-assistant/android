package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mediaplayctrls_widgets")
data class MediaPlayerControlsWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "entityId")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "showSkip")
    val showSkip: Boolean,
    @ColumnInfo(name = "showSeek")
    val showSeek: Boolean,
    @ColumnInfo(name = "showVolume")
    val showVolume: Boolean,
    @ColumnInfo(name = "showSource", defaultValue = "false")
    val showSource: Boolean,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null
) : WidgetEntity, ThemeableWidgetEntity
