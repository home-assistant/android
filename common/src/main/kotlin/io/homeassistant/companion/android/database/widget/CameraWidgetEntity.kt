package io.homeassistant.companion.android.database.widget

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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
