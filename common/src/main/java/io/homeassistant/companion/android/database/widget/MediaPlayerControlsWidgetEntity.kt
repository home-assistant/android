package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mediaplayctrls_widgets")
data class MediaPlayerControlsWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "entityId")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "showSkip")
    val showSkip: Boolean,
    @ColumnInfo(name = "showSeek")
    val showSeek: Boolean
)
