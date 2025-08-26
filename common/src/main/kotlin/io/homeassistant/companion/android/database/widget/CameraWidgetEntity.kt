package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_widgets")
data class CameraWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "tap_action", defaultValue = "REFRESH")
    val tapAction: WidgetTapAction,
) : WidgetEntity<CameraWidgetEntity> {
    override fun copyWithWidgetId(appWidgetId: Int): CameraWidgetEntity {
        return copy(id = appWidgetId)
    }
}
